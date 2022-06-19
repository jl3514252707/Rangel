package rangel.extaction.ambulance;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionLoad;
import adf.core.agent.action.ambulance.ActionUnload;
import adf.core.agent.action.common.ActionMove;
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
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

/**
 * 联合动作:运输
 *
 * @author 软工20-2金磊
 */
public class RangelExtActionTransport extends RangelExtAction {

    private static final LogHelper LOGGER = new LogHelper("AMBULANCE");

    public RangelExtActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;

        switch (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE, PRECOMPUTED, NON_PRECOMPUTE -> this.pathPlanning = moduleManager.getModule(
                    "RangelExtActionTransport.PathPlanning",
                    "adf.impl.module.algorithm.DijkstraPathPlanning");
        }
    }


    @Override
    public ExtAction calc() {
        this.result = null;
        //救护队自己
        AmbulanceTeam agent = (AmbulanceTeam) this.agentInfo.me();
        //运输的人
        Human transportHuman = this.agentInfo.someoneOnBoard();

        LOGGER.setAgentInfo(agentInfo);

        //如果运输的人不为null,
        if (transportHuman != null) {
            //则计算卸载动作
            this.result = this.calcUnload(agent, this.pathPlanning, transportHuman, this.target);
            //如果计算的结果不为null,则可以返回this
            if (this.result != null) {
                return this;
            }
        }

        //如果目标不为空
        if (this.target != null) {
            //则计算装载动作
            this.result = this.calcLoad(agent, this.pathPlanning, this.target);
        }
        return this;
    }


    /**
     * 计算装载
     *
     * @param agent        救护队
     * @param pathPlanning 路径规划
     * @param targetID     目标的id
     * @return adf.core.agent.action.Action 动作
     * @author 金磊
     * @since 2022/6/4 21:23
     */
    @Nullable
    private Action calcLoad(AmbulanceTeam agent, PathPlanning pathPlanning, EntityID targetID) {
        //获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        //如果目标不存在,则返回null
        if (targetEntity == null) {
            LOGGER.info("当前没有目标");
            return null;
        }
        //获得自身所在的位置
        EntityID agentPosition = agent.getPosition();
        //如果目标实体是一个人,
        if (targetEntity instanceof Human human) {
            //如果目标的位置没有确定,则返回null
            if (!human.isPositionDefined()) {
                LOGGER.info("不知道" + targetID + "的位置,不去救");
                return null;
            }
            //如果目标已经死亡,则返回null
            if (human.isHPDefined() && human.getHP() == 0) {
                LOGGER.info(targetID + "已经死亡了");
                return null;
            }
            //获得目标所在的位置
            EntityID targetPosition = human.getPosition();
            //如果自己已经到达目标位置,
            if (agentPosition.getValue() == targetPosition.getValue()) {
                //如果目标被掩埋,则返回null
                if (human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    LOGGER.info(targetID+"已被掩埋,无法运送");
                    return null;
                //否则目标没有被掩埋并且如果目标是平民,则可以搬起来,返回动作装载
                } else if (human.getStandardURN() == CIVILIAN) {
                    LOGGER.info("开始将"+targetID+"搬上担架");
                    return new ActionLoad(human.getID());
                }
            //否则没有到达目标位置,需要计算路径
            } else {
                //计算从自身位置到目标位置的路径
                List<EntityID> path = pathPlanning.getResult(agentPosition, targetPosition);
                //如果路径不为null并且路径的长度大于0,返回动作移动
                if (path != null && path.size() > 0) {
                    LOGGER.info("开始前往"+targetID+"所在的位置");
                    return new ActionMove(path);
                }
            }
            return null;
        }
        //如果目标实体是路径,
        if (targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //并且路障的位置已确定,则将目标实体更新为路径所在位置的实体
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标是区域,
        if (targetEntity instanceof Area) {
            //计算出从自身到目标的路径
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            //并且有路径,则返回动作移动
            if (path != null && path.size() > 0) {
                LOGGER.info("开始前往"+targetID+"所在的位置");
                return new ActionMove(path);
            }
        }
        return null;
    }


    /**
     * 计算卸载
     * @param agent 救护队
     * @param pathPlanning 路径规划
     * @param transportHuman 运输的人
     * @param targetID 目标的id,可能是人,也可能是区域
     * @return adf.core.agent.action.Action 动作
     * @author 软工20-2金磊
     * @since 2022/6/19
     */
    private Action calcUnload(AmbulanceTeam agent, PathPlanning pathPlanning, Human transportHuman, EntityID targetID) {
        //如果没有运输的人,返回null
        if (transportHuman == null) {
            return null;
        }
        //如果运输的人已经死亡,返回动作卸载
        if (transportHuman.isHPDefined() && transportHuman.getHP() == 0) {
            return new ActionUnload();
        }
        //获得自身的位置
        EntityID agentPosition = agent.getPosition();
        //如果没有目标,或者目标就是自己正在运输的人
        if (targetID == null || transportHuman.getID().getValue() == targetID.getValue()) {
            //获得自身所在位置的实体
            StandardEntity position = this.worldInfo.getEntity(agentPosition);
            //如果自己已经到达避难所,返回动作卸载
            if (position != null && position.getStandardURN() == REFUGE) {
                return new ActionUnload();
            //否则前往避难所
            } else {
                //设置起点为自己所在的位置
                pathPlanning.setFrom(agentPosition);
                //设置终点为避难所
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                //计算路径
                List<EntityID> path = pathPlanning.calc().getResult();
                //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
                if (path != null && path.size() > 0) {
                    return new ActionMove(path);
                }
            }
        }
        if (targetID == null) {
            return null;
        }
        //获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        //如果目标实体不为null,并且目标实体是路障,
        if (targetEntity != null && targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //如果路障的位置已知,
            if (blockade.isPositionDefined()) {
                //获取路障所在位置的实体
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标实体是区域,
        if (targetEntity instanceof Area) {
            //并且自身已经到达目标区域,返回动作卸载
            if (agentPosition.getValue() == targetID.getValue()) {
                return new ActionUnload();
            //否则前往目标地点
            } else {
                //设置起点为自己所在的位置
                pathPlanning.setFrom(agentPosition);
                //设置终点为目标区域
                pathPlanning.setDestination(targetID);
                //计算路径
                List<EntityID> path = pathPlanning.calc().getResult();
                //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
                if (path != null && path.size() > 0) {
                    return new ActionMove(path);
                }
            }
        //否则如果目标是个人,
        } else if (targetEntity instanceof Human human) {
            //并且位置确定,则计算前往避难所的动作
            if (human.isPositionDefined()) {
                return calcRefugeAction(agent, pathPlanning, Lists.newArrayList(human.getPosition()));
            }
            //没有计算处理,则继续
            //设置起点为自己所在的位置
            pathPlanning.setFrom(agentPosition);
            //设置终点为避难所
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
            //计算路径
            List<EntityID> path = pathPlanning.calc().getResult();
            //如果路径不为null,并且长度大于0,前往目标地点,返回动作移动
            if (path != null && path.size() > 0) {
                return new ActionMove(path);
            }
        }
        return null;
    }


    /**
     * 计算避难行动
     * @param human 救护队
     * @param pathPlanning 路径规划
     * @param targets 目标
     * @return adf.core.agent.action.Action 应该执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/19
     */
    @Nullable
    private Action calcRefugeAction(@NotNull Human human, PathPlanning pathPlanning, Collection<EntityID> targets) {
        //获得自身所在的位置
        EntityID position = human.getPosition();
        //获得地图中所有的避难所
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        //避难所的数量
        int size = refuges.size();
        //如果已经到达避难所,则把人放下,返回卸载动作
        if (refuges.contains(position)) {
            return new ActionUnload();
        }
        List<EntityID> firstResult = null;
        //遍历避难所,找到第一个合适的避难所并前往
        while (refuges.size() > 0) {
            //设置起点为自身所在的位置
            pathPlanning.setFrom(position);
            //设置终点为避难所的位置
            pathPlanning.setDestination(refuges);
            //计算出路径
            List<EntityID> path = pathPlanning.calc().getResult();
            //如果路径不为null并且路径的长度大于0
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    //如果没有别的目标就可以结束循环了,否则需要继续计算出从避难所到目标的路径
                    if (targets == null || targets.isEmpty()) {
                        break;
                    }
                }
                //路径的最后一个点即为避难所的id
                EntityID refugeID = path.get(path.size() - 1);
                //设置起点为避难所的位置
                pathPlanning.setFrom(refugeID);
                //终点为目标的位置
                pathPlanning.setDestination(targets);
                //计算出路径
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                //如果从避难所到目标有路,则可以前往该避难所,返回动作移动
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                //否则该避难所不合适,将其移除
                refuges.remove(refugeID);
                //如果移除失败,结束循环
                if (size == refuges.size()) {
                    break;
                }
                //更新避难所的数量
                size = refuges.size();
            } else {
                break;
            }
        }
        //如果找到了,则前往该避难,返回动作移动;如果没找到,则返回null
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}