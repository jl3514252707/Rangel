package rangel.extaction.police;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.action.police.ActionClear;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rangel.extaction.RangelExtAction;
import rangel.utils.LogHelper;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;
import java.util.stream.Collectors;

public class RangelExtActionClear extends RangelExtAction {

    private static final LogHelper LOGGER = new LogHelper("POLICE");

    /**
     * 清理能力
     */
    private final int clearDistance;

    /**
     * 强制移动(如果智能体在一定时间内没有移动,则强制其移动)
     */
    private final int forcedMove;

    /**
     * 路径点缓存
     */
    private final Map<EntityID, Set<Point2D>> movePointCache;

    /**
     * 旧的清理点X坐标
     */
    private int oldClearX;

    /**
     * 旧的清理点Y坐标
     */
    private int oldClearY;

    /**
     * 当前重复次数
     */
    private int count;


    public RangelExtActionClear(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.clearDistance = si.getClearRepairDistance();
        this.forcedMove = developData.getInteger("adf.impl.extaction.DefaultExtActionClear.forcedMove", 3);
        this.thresholdRest = developData.getInteger("adf.impl.extaction.DefaultExtActionClear.rest", 100);

        this.target = null;
        this.movePointCache = new HashMap<>();
        this.oldClearX = 0;
        this.oldClearY = 0;
        this.count = 0;

        switch (si.getMode()) {
            case PRECOMPUTATION_PHASE, PRECOMPUTED, NON_PRECOMPUTE -> this.pathPlanning = moduleManager.getModule(
                    "RangelExtActionClear.PathPlanning",
                    "adf.impl.module.algorithm.DijkstraPathPlanning");
        }

        LOGGER.setAgentInfo(agentInfo);
    }


    @Override
    public ExtAction setTarget(EntityID target) {
        this.target = null;
        StandardEntity entity = this.worldInfo.getEntity(target);
        if (entity != null) {
            if (entity instanceof Road) {
                this.target = target;
            } else if (entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
                this.target = ((Blockade) entity).getPosition();
            } else if (entity instanceof Building) {
                this.target = target;
            }
        }
        return this;
    }


    @Override
    public ExtAction calc() {
        this.result = null;
        PoliceForce policeForce = (PoliceForce) this.agentInfo.me();

        //如果警察需要休息
        if (this.needRest(policeForce)) {
            List<EntityID> list = new ArrayList<>();
            //并且已经有目标,则将其保存到列表中
            if (this.target != null) {
                list.add(this.target);
            }
            this.result = this.calcRest(policeForce, this.pathPlanning, list);
            if (this.result != null) {
                return this;
            }
        }

        if (this.target == null) {
            return this;
        }

        //警察的位置
        EntityID agentPosition = policeForce.getPosition();
        //目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
        //当前所在的实体
        StandardEntity positionEntity = this.worldInfo.getEntity(agentPosition);
        //如果目标实体不是一个区域,则直接return
        if (!(targetEntity instanceof Area)) {
            return this;
        }
        //TODO
        if (positionEntity instanceof Road) {
            this.result = this.getRescueAction(policeForce, (Road) positionEntity);
            if (this.result != null) {
                return this;
            }
        }
        //如果已到达目标地点,则执行区域清理动作
        if (agentPosition.equals(this.target)) {
            this.result = this.getAreaClearAction(policeForce, targetEntity);
        } else if (((Area) targetEntity).getEdgeTo(agentPosition) != null) {
            this.result = this.getNeighbourPositionAction(policeForce, (Area) targetEntity);
        } else {
            //获取前往目标地点的路径
            List<EntityID> path = this.pathPlanning.getResult(agentPosition, this.target);
            if (path != null && path.size() > 0) {
                int index = path.indexOf(agentPosition);
                if (index == -1) {
                    Area area = (Area) positionEntity;
                    for (int i = 0; i < path.size(); i++) {
                        if (Objects.requireNonNull(area).getEdgeTo(path.get(i)) != null) {
                            index = i;
                            break;
                        }
                    }
                } else {
                    index++;
                }
                if (index >= 0 && index < (path.size())) {
                    StandardEntity entity = this.worldInfo.getEntity(path.get(index));
                    this.result = this.getNeighbourPositionAction(policeForce, (Area) Objects.requireNonNull(entity));
                    if (this.result != null && this.result.getClass() == ActionMove.class) {
                        if (!((ActionMove) this.result).getUsePosition()) {
                            this.result = null;
                        }
                    }
                }
                if (this.result == null) {
                    this.result = new ActionMove(path);
                }
            }
        }
        return this;
    }


    /**
     * 获取救援行动
     *
     * @param police 警察
     * @param road   路
     * @return adf.core.agent.action.Action 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Nullable
    private Action getRescueAction(PoliceForce police, @NotNull Road road) {
        //如果路不是封锁的则不需要帮助清理路障
        if (!road.isBlockadesDefined()) {
            return null;
        }
        //该路上路障的集合
        Collection<Blockade> blockades = this.worldInfo.getBlockades(road).stream().filter(Blockade::isApexesDefined).collect(Collectors.toSet());
        //地图上所有救护队和消防队的集合
        Collection<StandardEntity> agents = this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.FIRE_BRIGADE);
        //当前警察的坐标
        double policeX = police.getX();
        double policeY = police.getY();
        double minDistance = Double.MAX_VALUE;
        Action moveAction = null;
        //遍历所有的救护队和消防队智能体
        for (StandardEntity entity : agents) {
            Human human = (Human) entity;
            //如果该智能体的位置没有确定或者不在这条路上,则不需要帮助
            if (!human.isPositionDefined() || human.getPosition().getValue() != road.getID().getValue()) {
                continue;
            }
            //该智能体的位置
            double humanX = human.getX();
            double humanY = human.getY();
            ActionClear actionClear = null;
            for (Blockade blockade : blockades) {
                //如果该智能体不在路障里面 TODO
                if (!this.isInside(humanX, humanY, blockade.getApexes())) {
                    continue;
                }
                //获取警察和该智能体之间的距离
                double distance = this.getDistance(policeX, policeY, humanX, humanY);
                //TODO
                if (this.intersect(policeX, policeY, humanX, humanY, road)) {
                    //TODO
                    Action action = this.getIntersectEdgeAction(policeX, policeY, humanX, humanY, road);
                    if (action.getClass() == ActionClear.class) {
                        if (actionClear == null) {
                            actionClear = (ActionClear) action;
                            continue;
                        }
                        if (actionClear.getTarget() != null) {
                            Blockade another = (Blockade) this.worldInfo
                                    .getEntity(actionClear.getTarget());
                            if (another != null && this.intersect(blockade, another)) {
                                return new ActionClear(another);
                            }
                            int anotherDistance = this.worldInfo.getDistance(police, another);
                            int blockadeDistance = this.worldInfo.getDistance(police,
                                    blockade);
                            if (anotherDistance > blockadeDistance) {
                                return action;
                            }
                        }
                        return actionClear;
                    } else if (action.getClass() == ActionMove.class
                            && distance < minDistance) {
                        minDistance = distance;
                        moveAction = action;
                    }
                } else if (this.intersect(policeX, policeY, humanX, humanY, blockade)) {
                    Vector2D vector = this.scaleClear(this.getVector(policeX, policeY, humanX, humanY));
                    int clearX = (int) (policeX + vector.getX());
                    int clearY = (int) (policeY + vector.getY());
                    vector = this.scaleBackClear(vector);
                    int startX = (int) (policeX + vector.getX());
                    int startY = (int) (policeY + vector.getY());
                    if (this.intersect(startX, startY, clearX, clearY, blockade)) {
                        if (actionClear == null) {
                            actionClear = new ActionClear(clearX, clearY, blockade);
                        } else {
                            if (actionClear.getTarget() != null) {
                                Blockade another = (Blockade) this.worldInfo
                                        .getEntity(actionClear.getTarget());
                                if (another != null && this.intersect(blockade, another)) {
                                    return new ActionClear(another);
                                }
                                int distance1 = this.worldInfo.getDistance(police, another);
                                int distance2 = this.worldInfo.getDistance(police, blockade);
                                if (distance1 > distance2) {
                                    return new ActionClear(clearX, clearY, blockade);
                                }
                            }
                            return actionClear;
                        }
                    } else if (distance < minDistance) {
                        minDistance = distance;
                        moveAction = new ActionMove(Lists.newArrayList(road.getID()),
                                (int) humanX, (int) humanY);
                    }
                }
            }
            if (actionClear != null) {
                return actionClear;
            }
        }
        return moveAction;
    }


    /**
     * 获取区域清理动作
     *
     * @param police       执行清理动作的警察
     * @param targetEntity 要清理的目标
     * @return adf.core.agent.action.Action 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Nullable
    private Action getAreaClearAction(PoliceForce police, StandardEntity targetEntity) {
        //如果目标实体是建筑,则直接返回一个空动作
        if (targetEntity instanceof Building) {
            return null;
        }
        Road road = (Road) targetEntity;
        //如果路的封锁情况没有确定或者路上没有路障,则直接返回一个空动作
        if (!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
            return null;
        }
        //获取该路段上的所有路障
        Collection<Blockade> blockades = this.worldInfo.getBlockades(road).stream().filter(Blockade::isApexesDefined).collect(Collectors.toSet());

        //先用第一种方式minDistance去找要清理的路障
        int minDistance = Integer.MAX_VALUE;
        Blockade clearBlockade = null;
        for (Blockade blockade : blockades) {
            for (Blockade another : blockades) {
                //如果两个路障不为同一个并且相交
                if (!blockade.getID().equals(another.getID()) && this.intersect(blockade, another)) {
                    int distance1 = this.worldInfo.getDistance(police, blockade);
                    int distance2 = this.worldInfo.getDistance(police, another);
                    //找出一个距离警察最近的路障
                    if (distance1 <= distance2 && distance1 < minDistance) {
                        minDistance = distance1;
                        clearBlockade = blockade;
                    } else if (distance2 < minDistance) {
                        minDistance = distance2;
                        clearBlockade = another;
                    }
                }
            }
        }
        if (clearBlockade != null) {
            //如果与路障间的距离小于清理能力,可以直接清理
            if (minDistance < this.clearDistance) {
                return new ActionClear(clearBlockade);
            } else {
                //否则需要先走到路障的位置
                return new ActionMove(Lists.newArrayList(police.getPosition()), clearBlockade.getX(), clearBlockade.getY());
            }
        }

        //第一种方式没找到就用第二种方式minPointDistance去找
        double agentX = police.getX();
        double agentY = police.getY();
        double minPointDistance = Double.MAX_VALUE;
        int clearX = 0;
        int clearY = 0;
        for (Blockade blockade : blockades) {
            //获得路障的所有顶点
            int[] apexes = blockade.getApexes();
            //找出一个距离警察最近的顶点
            for (int i = 0; i < (apexes.length - 2); i += 2) {
                double distance = this.getDistance(agentX, agentY, apexes[i], apexes[i + 1]);
                if (distance < minPointDistance) {
                    clearBlockade = blockade;
                    minPointDistance = distance;
                    clearX = apexes[i];
                    clearY = apexes[i + 1];
                }
            }
        }
        if (clearBlockade != null) {
            //如果与路障间的距离小于清理能力,可以直接清理
            if (minPointDistance < this.clearDistance) {
                Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, clearX, clearY));
                clearX = (int) (agentX + vector.getX());
                clearY = (int) (agentY + vector.getY());
                return new ActionClear(clearX, clearY, clearBlockade);
            }
            //否则需要先走到路障的位置
            return new ActionMove(Lists.newArrayList(police.getPosition()), clearX, clearY);
        }
        return null;
    }


    /**
     * 获取邻居位置
     *
     * @param police 警察
     * @param target 要清理的目标
     * @return adf.core.agent.action.Action 执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/8
     */
    @Nullable
    private Action getNeighbourPositionAction(@NotNull PoliceForce police, @NotNull Area target) {
        //获取警察的位置坐标
        double agentX = police.getX();
        double agentY = police.getY();
        //获取警察位置所在的实体
        StandardEntity position = Objects.requireNonNull(this.worldInfo.getPosition(police));
        //获取目标位置与警察位置相邻的边
        Edge edge = target.getEdgeTo(position.getID());
        if (edge == null) {
            return null;
        }
        //如果警察位置是一条路
        if (position instanceof Road road) {
            //并且路上有路障
            if (road.isBlockadesDefined() && road.getBlockades().size() > 0) {
                double midX = (edge.getStartX() + edge.getEndX()) / 2d;
                double midY = (edge.getStartY() + edge.getEndY()) / 2d;
                if (this.intersect(agentX, agentY, midX, midY, road)) {
                    return this.getIntersectEdgeAction(agentX, agentY, edge, road);
                }
                ActionClear actionClear = null;
                ActionMove actionMove = null;
                Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, midX, midY));
                int clearX = (int) (agentX + vector.getX());
                int clearY = (int) (agentY + vector.getY());
                vector = this.scaleBackClear(vector);
                int startX = (int) (agentX + vector.getX());
                int startY = (int) (agentY + vector.getY());
                for (Blockade blockade : this.worldInfo.getBlockades(road)) {
                    //如果路障是空的或者路障的顶点没有确定,遍历下一个
                    if (blockade == null || !blockade.isApexesDefined()) {
                        continue;
                    }
                    if (this.intersect(startX, startY, midX, midY, blockade)) {
                        if (this.intersect(startX, startY, clearX, clearY, blockade)) {
                            if (actionClear == null) {
                                actionClear = new ActionClear(clearX, clearY, blockade);
                                //如果现在的清理点和之前的清理点一样,
                                if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY)) {
                                    //并且当前重复的次数大于了强制移动规定的次数.说明警察卡在了这个位置,
                                    if (this.count >= this.forcedMove) {
                                        this.count = 0;
                                        //就强制其移动到清理位置
                                        return new ActionMove(Lists.newArrayList(road.getID()), clearX, clearY);
                                    }
                                    this.count++;
                                }
                                this.oldClearX = clearX;
                                this.oldClearY = clearY;
                            } else {
                                if (actionClear.getTarget() != null) {
                                    Blockade another = (Blockade) this.worldInfo.getEntity(actionClear.getTarget());
                                    if (another != null && this.intersect(blockade, another)) {
                                        return new ActionClear(another);
                                    }
                                }
                                return actionClear;
                            }
                        } else if (actionMove == null) {
                            actionMove = new ActionMove(Lists.newArrayList(road.getID()), (int) midX, (int) midY);
                        }
                    }
                }
                if (actionClear != null) {
                    return actionClear;
                } else if (actionMove != null) {
                    return actionMove;
                }
            }
        }
        //如果目标是一条路,
        if (target instanceof Road road) {
            //并且路的封锁情况没有确定或者路上没有路障,
            if (!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                //就直接移动到目标位置
                return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
            }
            Blockade clearBlockade = null;
            double minPointDistance = Double.MAX_VALUE;
            int clearX = 0;
            int clearY = 0;
            for (EntityID id : road.getBlockades()) {
                Blockade blockade = (Blockade) this.worldInfo.getEntity(id);
                if (blockade != null && blockade.isApexesDefined()) {
                    int[] apexes = blockade.getApexes();
                    for (int i = 0; i < (apexes.length - 2); i += 2) {
                        double distance = this.getDistance(agentX, agentY, apexes[i], apexes[i + 1]);
                        if (distance < minPointDistance) {
                            clearBlockade = blockade;
                            minPointDistance = distance;
                            clearX = apexes[i];
                            clearY = apexes[i + 1];
                        }
                    }
                }
            }
            if (clearBlockade != null && minPointDistance < this.clearDistance) {
                Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, clearX, clearY));
                clearX = (int) (agentX + vector.getX());
                clearY = (int) (agentY + vector.getY());
                if (this.equalsPoint(this.oldClearX, this.oldClearY, clearX, clearY)) {
                    if (this.count >= this.forcedMove) {
                        this.count = 0;
                        return new ActionMove(Lists.newArrayList(road.getID()), clearX, clearY);
                    }
                    this.count++;
                }
                this.oldClearX = clearX;
                this.oldClearY = clearY;
                return new ActionClear(clearX, clearY, clearBlockade);
            }
        }
        return new ActionMove(Lists.newArrayList(position.getID(), target.getID()));
    }


    /**
     * 获取相交边的动作
     *
     * @param agentX 警察的位置X坐标
     * @param agentY 警察的位置Y坐标
     * @param edge   相邻边
     * @param road   路
     * @return adf.core.agent.action.Action 要执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/8
     */
    @NotNull
    private Action getIntersectEdgeAction(double agentX, double agentY, @NotNull Edge edge, Road road) {
        double midX = (edge.getStartX() + edge.getEndX()) / 2d;
        double midY = (edge.getStartY() + edge.getEndY()) / 2d;
        return this.getIntersectEdgeAction(agentX, agentY, midX, midY, road);
    }


    /**
     * 获取相交边的动作
     *
     * @param agentX 警察的位置X坐标
     * @param agentY 警察的位置Y坐标
     * @param pointX 目标的位置X坐标
     * @param pointY 目标的位置Y坐标
     * @param road   路
     * @return adf.core.agent.action.Action 要执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @NotNull
    private Action getIntersectEdgeAction(double agentX, double agentY, double pointX, double pointY, Road road) {
        //获取所有的路径点
        Set<Point2D> movePoints = this.getMovePoints(road);
        Point2D bestPoint = null;
        double bestDistance = Double.MAX_VALUE;
        for (Point2D p : movePoints) {
            //如果警察与路径点的连线没有与路相交,
            if (!this.intersect(agentX, agentY, p.getX(), p.getY(), road)) {
                //并且目标与路径点的连线没有与路相交
                if (!this.intersect(pointX, pointY, p.getX(), p.getY(), road)) {
                    //获得目标与路径点间的距离
                    double distance = this.getDistance(pointX, pointY, p.getX(), p.getY());
                    //如果距离小于最佳距离,将其赋值为最佳距离
                    if (distance < bestDistance) {
                        bestPoint = p;
                        bestDistance = distance;
                    }
                }
            }
        }
        //如果得到了最佳距离,
        if (bestPoint != null) {
            double pX = bestPoint.getX();
            double pY = bestPoint.getY();
            //并且路的封锁情况是已知的,移动到该路径点
            if (!road.isBlockadesDefined()) {
                return new ActionMove(Lists.newArrayList(road.getID()), (int) pX, (int) pY);
            }
            ActionClear actionClear = null;
            ActionMove actionMove = null;
            Vector2D vector = this.scaleClear(this.getVector(agentX, agentY, pX, pY));
            int clearX = (int) (agentX + vector.getX());
            int clearY = (int) (agentY + vector.getY());
            vector = this.scaleBackClear(vector);
            int startX = (int) (agentX + vector.getX());
            int startY = (int) (agentY + vector.getY());
            for (Blockade blockade : this.worldInfo.getBlockades(road)) {
                if (this.intersect(startX, startY, pX, pY, blockade)) {
                    if (this.intersect(startX, startY, clearX, clearY, blockade)) {
                        if (actionClear == null) {
                            actionClear = new ActionClear(clearX, clearY, blockade);
                        } else {
                            if (actionClear.getTarget() != null) {
                                Blockade another = (Blockade) this.worldInfo.getEntity(actionClear.getTarget());
                                if (another != null && this.intersect(blockade, another)) {
                                    return new ActionClear(another);
                                }
                            }
                            return actionClear;
                        }
                    } else if (actionMove == null) {
                        actionMove = new ActionMove(Lists.newArrayList(road.getID()), (int) pX, (int) pY);
                    }
                }
            }
            if (actionClear != null) {
                return actionClear;
            } else if (actionMove != null) {
                return actionMove;
            }
        }
        Action action = this.getAreaClearAction((PoliceForce) this.agentInfo.me(), road);
        if (action == null) {
            action = new ActionMove(Lists.newArrayList(road.getID()), (int) pointX, (int) pointY);
        }
        return action;
    }


    /**
     * 比较两个点是否在一个范围内
     *
     * @param p1X 一个点的X坐标
     * @param p1Y 一个点的Y坐标
     * @param p2X 另一个点的X坐标
     * @param p2Y 另一个点的Y坐标
     * @return boolean (true在:false不在)
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y) {
        double range = 1000.0;
        return (p2X - p1X < range && p1X - p2X < range) && (p2Y - p1Y < range && p1Y - p2Y < range);
    }


    /**
     * 判断获取的路径点是否可用
     *
     * @param pX   路径点X坐标
     * @param pY   路径点Y坐标
     * @param apex 路的顶点数组
     * @return boolean (true可用:false不可用)
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private boolean isInside(double pX, double pY, int @NotNull [] apex) {
        Point2D p = new Point2D(pX, pY);
        //以该路径点和路的最后一个顶点构成一条连线
        Vector2D v1 = (new Point2D(apex[apex.length - 2], apex[apex.length - 1])).minus(p);
        //以该路径点和路的第一个顶点构成一条连线
        Vector2D v2 = (new Point2D(apex[0], apex[1])).minus(p);
        //得到两条线之间的角度
        double theta = this.getAngle(v1, v2);

        for (int i = 0; i < apex.length - 2; i += 2) {
            v1 = (new Point2D(apex[i], apex[i + 1])).minus(p);
            v2 = (new Point2D(apex[i + 2], apex[i + 3])).minus(p);
            theta += this.getAngle(v1, v2);
        }
        return Math.round(Math.abs((theta / 2) / Math.PI)) >= 1;
    }


    /**
     * 判断警察和目标智间的连线是否与路相交
     *
     * @param agentX 警察的位置X坐标
     * @param agentY 警察的位置Y坐标
     * @param pointX 目标的位置X坐标
     * @param pointY 目标的位置Y坐标
     * @param area   路
     * @return boolean (true相交||false不相交)
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private boolean intersect(double agentX, double agentY, double pointX, double pointY, @NotNull Area area) {
        //遍历路的每一条边
        for (Edge edge : area.getEdges()) {
            double startX = edge.getStartX();
            double startY = edge.getStartY();
            double endX = edge.getEndX();
            double endY = edge.getEndY();
            if (java.awt.geom.Line2D.linesIntersect(
                    agentX, agentY,
                    pointX, pointY,
                    startX, startY,
                    endX, endY)) {
                double midX = (edge.getStartX() + edge.getEndX()) / 2d;
                double midY = (edge.getStartY() + edge.getEndY()) / 2d;
                if (!equalsPoint(pointX, pointY, midX, midY) && !equalsPoint(agentX, agentY, midX, midY)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 判断两个路障是否相交
     *
     * @param blockade 是个路障
     * @param another  也是一个路障
     * @return boolean (true相交||false不相交)
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private boolean intersect(@NotNull Blockade blockade, Blockade another) {
        //如果两个路障的顶点已知
        if (blockade.isApexesDefined() && another.isApexesDefined()) {
            //获取两个路障的顶点
            int[] apexes0 = blockade.getApexes();
            int[] apexes1 = another.getApexes();
            //遍历两个路障的所有每两个顶点的连线,如果相交,则说明两个路障有相交部分
            for (int i = 0; i < (apexes0.length - 2); i += 2) {
                for (int j = 0; j < (apexes1.length - 2); j += 2) {
                    if (java.awt.geom.Line2D.linesIntersect(
                            apexes0[i], apexes0[i + 1],
                            apexes0[i + 2], apexes0[i + 3],
                            apexes1[j], apexes1[j + 1],
                            apexes1[j + 2], apexes1[j + 3])) {
                        return true;
                    }
                }
            }
            for (int i = 0; i < (apexes0.length - 2); i += 2) {
                if (java.awt.geom.Line2D.linesIntersect(
                        apexes0[i], apexes0[i + 1],
                        apexes0[i + 2], apexes0[i + 3],
                        apexes1[apexes1.length - 2], apexes1[apexes1.length - 1],
                        apexes1[0], apexes1[1])) {
                    return true;
                }
            }
            for (int j = 0; j < (apexes1.length - 2); j += 2) {
                if (java.awt.geom.Line2D.linesIntersect(
                        apexes0[apexes0.length - 2], apexes0[apexes0.length - 1],
                        apexes0[0], apexes0[1],
                        apexes1[j], apexes1[j + 1],
                        apexes1[j + 2], apexes1[j + 3])) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 判断警察和目标间的连线是否与路障相交
     *
     * @param agentX   警察的位置X坐标
     * @param agentY   警察的位置Y坐标
     * @param pointX   目标的位置X坐标
     * @param pointY   目标的位置Y坐标
     * @param blockade 路障
     * @return boolean (true相交||false不相交)
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private boolean intersect(double agentX, double agentY, double pointX, double pointY, @NotNull Blockade blockade) {
        //获取路障所有顶点的连线
        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()), true);
        //遍历每条连线
        for (Line2D line : lines) {
            Point2D start = line.getOrigin();
            Point2D end = line.getEndPoint();
            double startX = start.getX();
            double startY = start.getY();
            double endX = end.getX();
            double endY = end.getY();
            //指定第一条线的起点为警察的位置,终点为目标的位置,第二条线为路障中的一条线,如果与这条线相交,说明与路障相交
            if (java.awt.geom.Line2D.linesIntersect(agentX, agentY, pointX, pointY, startX, startY, endX, endY)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 获取两个点之间的距离
     *
     * @param fromX 一个点的X坐标
     * @param fromY 一个点的Y坐标
     * @param toX   另一个点的X坐标
     * @param toY   另一个点的Y坐标
     * @return double 两个点之间的距离
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private double getDistance(double fromX, double fromY, double toX, double toY) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        return Math.hypot(dx, dy);
    }


    /**
     * 获取两条线的夹角
     *
     * @param v1 一条线的向量
     * @param v2 另一条线向量
     * @return double 角度
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private double getAngle(@NotNull Vector2D v1, @NotNull Vector2D v2) {
        double flag = (v1.getX() * v2.getY()) - (v1.getY() * v2.getX());
        double angle = Math.acos(((v1.getX() * v2.getX()) + (v1.getY() * v2.getY())) / (v1.getLength() * v2.getLength()));
        if (flag > 0) {
            return angle;
        }
        if (flag < 0) {
            return -1 * angle;
        }
        return 0.0D;
    }


    /**
     * 获取给定两个点之间连线的向量
     *
     * @param fromX 一个点的X坐标
     * @param fromY 一个点的Y坐标
     * @param toX   另一个点的X坐标
     * @param toY   另一个点的Y坐标
     * @return rescuecore2.misc.geometry.Vector2D 向量
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private Vector2D getVector(double fromX, double fromY, double toX, double toY) {
        return (new Point2D(toX, toY)).minus(new Point2D(fromX, fromY));
    }


    /**
     * 矩形框清理<br>
     * 对当前目标路障，警察行进前方生成一个矩形框，在矩形框范围内的障碍会消失,如果路障没有被矩形框覆盖,则不会变化<br>
     * 速度相对很快
     *
     * @param vector 向量
     * @return rescuecore2.misc.geometry.Vector2D 向量
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private Vector2D scaleClear(@NotNull Vector2D vector) {
        return vector.normalised().scale(this.clearDistance);
    }


    /**
     * 区域清理<br>
     * 警察对目标路障执行清理动作，路障开始以一定速度逐渐向中心收缩变小，最终消失<br>
     * 耗时很久
     *
     * @param vector 向量
     * @return rescuecore2.misc.geometry.Vector2D 向量
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    private Vector2D scaleBackClear(@NotNull Vector2D vector) {
        return vector.normalised().scale(-510);
    }


    /**
     * 获得路径点
     *
     * @param road 路
     * @return java.util.Set<rescuecore2.misc.geometry.Point2D> 路径点
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @NotNull
    private Set<Point2D> getMovePoints(@NotNull Road road) {
        //从路径点缓存中取出该条路对应的路径点
        Set<Point2D> points = this.movePointCache.get(road.getID());
        //如果没有缓存
        if (points == null) {
            points = new HashSet<>();
            //获取这条路的所有顶点
            int[] apex = road.getApexList();
            for (int i = 0; i < apex.length; i += 2) {
                for (int j = i + 2; j < apex.length; j += 2) {
                    double midX = (apex[i] + apex[j]) / 2d;
                    double midY = (apex[i + 1] + apex[j + 1]) / 2d;
                    if (this.isInside(midX, midY, apex)) {
                        points.add(new Point2D(midX, midY));
                    }
                }
            }
            //如果获取的路径点在路的边缘,则将其移除
            for (Edge edge : road.getEdges()) {
                double midX = (edge.getStartX() + edge.getEndX()) / 2d;
                double midY = (edge.getStartY() + edge.getEndY()) / 2d;
                points.remove(new Point2D(midX, midY));
            }
            this.movePointCache.put(road.getID(), points);
        }
        return points;
    }


    private boolean needRest(Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if (damage == 0 || hp == 0) {
            return false;
        }
        int activeTime = (hp / damage) + ((hp % damage) != 0 ? 1 : 0);
        if (this.kernelTime == -1) {
            try {
                this.kernelTime = this.scenarioInfo.getKernelTimesteps();
            } catch (NoSuchConfigOptionException e) {
                this.kernelTime = -1;
            }
        }
        return damage >= this.thresholdRest
                || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }


    private Action calcRest(Human human, PathPlanning pathPlanning,
                            Collection<EntityID> targets) {
        EntityID position = human.getPosition();
        Collection<EntityID> refuges = this.worldInfo
                .getEntityIDsOfType(StandardEntityURN.REFUGE);
        int currentSize = refuges.size();
        if (refuges.contains(position)) {
            return new ActionRest();
        }
        List<EntityID> firstResult = null;
        while (refuges.size() > 0) {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(refuges);
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    if (targets == null || targets.isEmpty()) {
                        break;
                    }
                }
                EntityID refugeID = path.get(path.size() - 1);
                pathPlanning.setFrom(refugeID);
                pathPlanning.setDestination(targets);
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                refuges.remove(refugeID);
                // remove failed
                if (currentSize == refuges.size()) {
                    break;
                }
                currentSize = refuges.size();
            } else {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}