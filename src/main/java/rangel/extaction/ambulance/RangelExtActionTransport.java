package rangel.extaction.ambulance;

import adf.core.agent.action.Action;
import adf.core.agent.action.ambulance.ActionLoad;
import adf.core.agent.action.ambulance.ActionRescue;
import adf.core.agent.action.ambulance.ActionUnload;
import adf.core.agent.action.common.ActionMove;
import adf.core.agent.action.common.ActionRest;
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
import rescuecore2.config.NoSuchConfigOptionException;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class RangelExtActionTransport extends RangelExtAction {

    public RangelExtActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager, developData);
        this.target = null;
        this.thresholdRest = developData.getInteger("ActionTransport.rest", 100);

        switch (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE, PRECOMPUTED, NON_PRECOMPUTE -> this.pathPlanning = moduleManager.getModule(
                    "RangelExtActionTransport.PathPlanning",
                    "adf.impl.module.algorithm.DijkstraPathPlanning");
        }
    }

    @Override
    public ExtAction calc() {
        this.result = null;
        AmbulanceTeam agent = (AmbulanceTeam) this.agentInfo.me();
        Human transportHuman = this.agentInfo.someoneOnBoard();

        if (transportHuman != null) {
            this.result = this.calcUnload(agent, this.pathPlanning, transportHuman,
                    this.target);
            if (this.result != null) {
                return this;
            }
        }
        if (this.needRest(agent)) {
            EntityID areaID = this.convertArea(this.target);
            ArrayList<EntityID> targets = new ArrayList<>();
            if (areaID != null) {
                targets.add(areaID);
            }
            this.result = this.calcRefugeAction(agent, this.pathPlanning, targets,
                    false);
            if (this.result != null) {
                return this;
            }
        }
        if (this.target != null) {
            this.result = this.calcRescue(agent, this.pathPlanning, this.target);
        }
        return this;
    }

    /**
     * 计算救援
     *
     * @param agent        救护队
     * @param pathPlanning 路径规划
     * @param targetID     目标的id
     * @return adf.core.agent.action.Action 动作类型
     * @author 金磊
     * @since 2022/6/4 21:23
     */
    @Nullable
    private Action calcRescue(AmbulanceTeam agent, PathPlanning pathPlanning, EntityID targetID) {
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        if (targetEntity == null) {
            return null;
        }
        EntityID agentPosition = agent.getPosition();
        if (targetEntity instanceof Human human) {
            if (!human.isPositionDefined()) {
                return null;
            }
            if (human.isHPDefined() && human.getHP() == 0) {
                return null;
            }
            EntityID targetPosition = human.getPosition();
            if (agentPosition.getValue() == targetPosition.getValue()) {
                if (human.isBuriednessDefined() && human.getBuriedness() > 0) {
                    return new ActionRescue(human);
                } else if (human.getStandardURN() == CIVILIAN) {
                    return new ActionLoad(human.getID());
                }
            } else {
                List<EntityID> path = pathPlanning.getResult(agentPosition,
                        targetPosition);
                if (path != null && path.size() > 0) {
                    return new ActionMove(path);
                }
            }
            return null;
        }
        if (targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        if (targetEntity instanceof Area) {
            List<EntityID> path = pathPlanning.getResult(agentPosition,
                    targetEntity.getID());
            if (path != null && path.size() > 0) {
                return new ActionMove(path);
            }
        }
        return null;
    }

    private Action calcUnload(AmbulanceTeam agent, PathPlanning pathPlanning, Human transportHuman, EntityID targetID) {
        if (transportHuman == null) {
            return null;
        }
        if (transportHuman.isHPDefined() && transportHuman.getHP() == 0) {
            return new ActionUnload();
        }
        EntityID agentPosition = agent.getPosition();
        if (targetID == null
                || transportHuman.getID().getValue() == targetID.getValue()) {
            StandardEntity position = this.worldInfo.getEntity(agentPosition);
            if (position != null && position.getStandardURN() == REFUGE) {
                return new ActionUnload();
            } else {
                pathPlanning.setFrom(agentPosition);
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    return new ActionMove(path);
                }
            }
        }
        if (targetID == null) {
            return null;
        }
        StandardEntity targetEntity = this.worldInfo.getEntity(targetID);
        if (targetEntity != null && targetEntity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) targetEntity;
            if (blockade.isPositionDefined()) {
                targetEntity = this.worldInfo.getEntity(blockade.getPosition());
            }
        }
        if (targetEntity instanceof Area) {
            if (agentPosition.getValue() == targetID.getValue()) {
                return new ActionUnload();
            } else {
                pathPlanning.setFrom(agentPosition);
                pathPlanning.setDestination(targetID);
                List<EntityID> path = pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    return new ActionMove(path);
                }
            }
        } else if (targetEntity instanceof Human human) {
            if (human.isPositionDefined()) {
                return calcRefugeAction(agent, pathPlanning,
                        Lists.newArrayList(human.getPosition()), true);
            }
            pathPlanning.setFrom(agentPosition);
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                return new ActionMove(path);
            }
        }
        return null;
    }


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
        return damage >= this.thresholdRest
                || (activeTime + this.agentInfo.getTime()) < this.kernelTime;
    }

    @Nullable
    private EntityID convertArea(EntityID targetID) {
        StandardEntity entity = this.worldInfo.getEntity(targetID);
        if (entity == null) {
            return null;
        }
        if (entity instanceof Human human) {
            if (human.isPositionDefined()) {
                EntityID position = human.getPosition();
                if (this.worldInfo.getEntity(position) instanceof Area) {
                    return position;
                }
            }
        } else if (entity instanceof Area) {
            return targetID;
        } else if (entity.getStandardURN() == BLOCKADE) {
            Blockade blockade = (Blockade) entity;
            if (blockade.isPositionDefined()) {
                return blockade.getPosition();
            }
        }
        return null;
    }

    @Nullable
    private Action calcRefugeAction(@NotNull Human human, PathPlanning pathPlanning, Collection<EntityID> targets, boolean isUnload) {
        EntityID position = human.getPosition();
        Collection<EntityID> refuges = this.worldInfo
                .getEntityIDsOfType(StandardEntityURN.REFUGE);
        int size = refuges.size();
        if (refuges.contains(position)) {
            return isUnload ? new ActionUnload() : new ActionRest();
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
                if (size == refuges.size()) {
                    break;
                }
                size = refuges.size();
            } else {
                break;
            }
        }
        return firstResult != null ? new ActionMove(firstResult) : null;
    }
}