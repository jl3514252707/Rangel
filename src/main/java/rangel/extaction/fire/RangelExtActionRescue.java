package rangel.extaction.fire;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rangel.extaction.RangelExtAction;
import rangel.utils.LogHelper;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.BLOCKADE;

/**
 * 联合动作:救援
 *
 * @author 软工20-2金磊
 */
public class RangelExtActionRescue extends RangelExtAction {

    private static final LogHelper LOGGER=new LogHelper("FIRE");

    public RangelExtActionRescue(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
        this.thresholdRest = developData.getInteger("adf.impl.extaction.DefaultExtActionFireRescue.rest", 100);

        switch (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE, PRECOMPUTED, NON_PRECOMPUTE -> this.pathPlanning = moduleManager.getModule(
                    "RangelExtActionRescue.PathPlanning",
                    "adf.impl.module.algorithm.DijkstraPathPlanning");
        }
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        //消防队自己
        FireBrigade agent = (FireBrigade) this.agentInfo.me();

        LOGGER.setAgentInfo(agentInfo);

        //如果自己需要休息,
        if (this.needRest(agent)) {
            LOGGER.info("我需要休息");
            EntityID areaID = this.convertArea(this.target);
            ArrayList<EntityID> targets = new ArrayList<>();
            //如果自己现在还有目标,则将其存起来
            if (areaID != null) {
                targets.add(areaID);
            }
        }
        //如果当前目标不为空,则计算救援
        if (this.target != null) {
            this.result = this.calcRescue(agent, this.pathPlanning, this.target);
        }
        return this;
    }


    /**
     * 计算救援
     *
     * @param agent        执行动作的消防队
     * @param pathPlanning 路径规划
     * @param targetID     当前的目标
     * @return adf.core.agent.action.Action 应该执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    @Nullable
    private Action calcRescue(FireBrigade agent, PathPlanning pathPlanning, EntityID targetID) {
        //根据实体ID获得目标实体
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        if (targetEntity == null) {
            LOGGER.info("当前没有目标");
            return null;
        }
        //获得消防队的位置
        EntityID agentPosition = agent.getPosition();
        //如果目标实体是个人,
        if (targetEntity instanceof Human human) {
            //如果目标的位置没有确定,没法去救,直接返回空
            if (!human.isPositionDefined()) {
                LOGGER.info("不知道"+targetID+"的位置,不去救");
                return null;
            }
            //如果目标的血量为0,救不了了,直接返回空
            if (human.isHPDefined() && human.getHP() == 0) {
                LOGGER.info(targetID+"已经死亡了");
                return null;
            }
            //获得目标人类的位置
            EntityID targetPosition = human.getPosition();
            //如果消防队到达目标人类所在的位置,
            if (agentPosition.getValue() == targetPosition.getValue()) {
                //并且该人类的被掩埋程度大于0,开始救援,返回救援动作
                if (human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    LOGGER.info(targetID+"被掩埋了,开始救援");
                    return new ActionRescue(human);
                }
                //否则没有到达目标人类的所在的位置,
            } else {
                //计算出从自己到目标的路径
                List<EntityID> path = pathPlanning.getResult(agentPosition, targetPosition);
                //如果该路径不为空,并且路径的长度大于0,移动到目标所在的位置,返回动作移动
                if (path != null && path.size() > 0) {
                    LOGGER.info("并未到目标位置,前往"+targetID+"所在的位置");
                    return new ActionMove(path);
                }
            }
            return null;
        }
        //如果目标实体是路障,
        if (targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            //并且路障的位置是已知的,将目标实体赋值为该路障的所在位置的实体
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        //如果目标实体是一个区域,
        if (targetEntity instanceof Area) {
            //计算出从自己到目标的路径
            List<EntityID> path = pathPlanning.getResult(agentPosition, targetEntity.getID());
            //如果该路径不为空,并且路径的长度大于0,移动到目标所在的位置,返回动作移动
            if (path != null && path.size() > 0) {
                LOGGER.info("并未到目标位置,前往"+targetID+"所在的位置");
                return new ActionMove(path);
            }
        }
        return null;
    }


    /**
     * 判断是否需要休息
     *
     * @param agent 执行动作的智能体
     * @return boolean (true需要休息||false不休息)
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    private boolean needRest(@NotNull Human agent) {
        int hp = agent.getHP();
        int damage = agent.getDamage();
        if (hp == 0 || damage == 0) {
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
        return damage >= this.thresholdRest || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }


    /**
     * 将目标转换为一个区域,例如:<br>
     * •获得人所在区域的实体ID<br>
     * •获得路障所在区域的实体ID
     *
     * @param targetID 目标的实体ID
     * @return rescuecore2.worldmodel.EntityID
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    @Nullable
    private EntityID convertArea(EntityID targetID) {
        StandardEntity entity = this.worldInfo.getEntity(targetID);
        if (entity == null) {
            return null;
        }
        //如果目标实体是一个人,
        if (entity instanceof Human human) {
            //并且位置已知,
            if (human.isPositionDefined()) {
                //获得目标的位置
                EntityID position = human.getPosition();
                //如果该位置是一个区域,直接返回区域的实体ID
                if (this.worldInfo.getEntity(position) instanceof Area) {
                    return position;
                }
            }
            //如果目标是一个区域,直接返回该区域的实体ID
        } else if (entity instanceof Area) {
            return targetID;
            //否则如果是一个路障,
        } else if (entity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) entity;
            //并且该路障的位置已知,返回该路障的位置的实体ID
            if (blockade.isPositionDefined()) {
                return blockade.getPosition();
            }
        }
        return null;
    }
}