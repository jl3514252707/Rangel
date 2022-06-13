package rangel.extaction;

import adf.core.agent.action.Action;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RangelExtActionMove extends RangelExtAction {

    public RangelExtActionMove(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
        this.thresholdRest = developData
                .getInteger("adf.impl.extaction.DefaultExtActionMove.rest", 100);

        switch (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE, PRECOMPUTED, NON_PRECOMPUTE -> this.pathPlanning = moduleManager.getModule(
                    "RangelExtActionMove.PathPlanning",
                    "adf.impl.module.algorithm.DijkstraPathPlanning");
        }
    }

    @Override
    public ExtAction setTarget(EntityID target) {
        this.target = null;
        StandardEntity entity = this.worldInfo.getEntity(target);
        if (entity != null) {
            if (entity.getStandardURN().equals(StandardEntityURN.BLOCKADE)) {
                entity = this.worldInfo.getEntity(((Blockade) entity).getPosition());
            } else if (entity instanceof Human) {
                entity = this.worldInfo.getPosition((Human) entity);
            }

            if (entity instanceof Area) {
                this.target = entity.getID();
            }
        }
        return this;
    }


    @Override
    public ExtAction calc() {
        this.result = null;
        Human agent = (Human) this.agentInfo.me();

        if (this.needRest(agent)) {
            this.result = this.calcRest(agent, this.pathPlanning, this.target);
            if (this.result != null) {
                return this;
            }
        }
        if (this.target == null) {
            return this;
        }
        this.pathPlanning.setFrom(agent.getPosition());
        this.pathPlanning.setDestination(this.target);
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if (path != null && path.size() > 0) {
            this.result = new ActionMove(path);
        }
        return this;
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
     * 计算怎样前往避难所休息
     *
     * @param human        执行动作的智能体
     * @param pathPlanning 路径规划
     * @param target       当前的目标
     * @return adf.core.agent.action.Action 应该执行的动作
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    @Nullable
    private Action calcRest(@NotNull Human human, PathPlanning pathPlanning, EntityID target) {
        //获得自身的位置
        EntityID position = human.getPosition();
        //获得地图中的所有避难所
        Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        int currentSize = refuges.size();
        //如果自己的位置已经在避难所,可以直接休息
        if (refuges.contains(position)) {
            return new ActionRest();
        }
        List<EntityID> firstResult = null;
        while (refuges.size() > 0) {
            pathPlanning.setFrom(position);
            pathPlanning.setDestination(refuges);
            //获得自己到避难所之间的路径
            List<EntityID> path = pathPlanning.calc().getResult();
            //如果路径不为空并且路径的长度大于0,将其添加到首选中
            if (path != null && path.size() > 0) {
                if (firstResult == null) {
                    firstResult = new ArrayList<>(path);
                    if (target == null) {
                        break;
                    }
                }
                //路径的最后一个点是避难所
                EntityID refugeID = path.get(path.size() - 1);
                //从避难所回到目标
                pathPlanning.setFrom(refugeID);
                pathPlanning.setDestination(target);
                //计算从避难所到目标间的路径
                List<EntityID> fromRefugeToTarget = pathPlanning.calc().getResult();
                //如果路径不为空并且路径的长度大于0,就移动到目标处
                if (fromRefugeToTarget != null && fromRefugeToTarget.size() > 0) {
                    return new ActionMove(path);
                }
                //到此说明该避难所不符合条件,将其排除
                refuges.remove(refugeID);
                //移除失败
                if (currentSize == refuges.size()) {
                    break;
                }
                currentSize = refuges.size();
            } else {
                break;
            }
        }
        //如果首选不为空,就移动到该避难所,否则返回空
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}