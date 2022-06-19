package rangel.extaction;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.extaction.ExtAction;
import adf.core.component.module.algorithm.PathPlanning;
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

public abstract class RangelExtAction extends ExtAction {

    /**
     * 路径规划算法
     */
    protected PathPlanning pathPlanning;

    /**
     * 内核时间
     */
    protected int kernelTime;

    /**
     * 动作目标
     */
    protected EntityID target;


    public RangelExtAction(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
    }


    /**
     * 预计算启动
     *
     * @param precomputeData 预计算的数据
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Override
    public ExtAction precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        //同步内核时间
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 直接启动
     *
     * @param precomputeData 预计算的数据
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Override
    public ExtAction resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        //同步内核时间
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 准备
     *
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Override
    public ExtAction preparate() {
        super.preparate();
        if (this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        //同步内核时间
        try {
            this.kernelTime = this.scenarioInfo.getKernelTimesteps();
        } catch (NoSuchConfigOptionException e) {
            this.kernelTime = -1;
        }
        return this;
    }


    /**
     * 更新信息
     *
     * @param messageManager 消息管理器
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Override
    public ExtAction updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        return this;
    }


    /**
     * 设定动作的目标
     *
     * @param target 目标实体的ID
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    @Override
    public ExtAction setTarget(EntityID target) {
        this.target = null;
        if (target != null) {
            StandardEntity entity = this.worldInfo.getEntity(target);
            if (entity instanceof Human || entity instanceof Area) {
                this.target = target;
                return this;
            }
        }
        return this;
    }


    /**
     * 计算接下来应该执行的动作
     *
     * @return adf.core.component.extaction.ExtAction 动作
     * @author 软工20-2金磊
     * @since 2022/6/6
     */
    public abstract ExtAction calc();

}
