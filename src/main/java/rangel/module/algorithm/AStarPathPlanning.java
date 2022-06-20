package rangel.module.algorithm;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.agent.precompute.PrecomputeData;
import adf.core.component.module.algorithm.PathPlanning;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.Area;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

/**
 * 基于AStar算法的路径规划
 *
 * @author 软工20-2金磊
 */
public class AStarPathPlanning extends PathPlanning {

    private Map<EntityID, Set<EntityID>> graph;

    private EntityID from;
    private Collection<EntityID> targets;
    private List<EntityID> result;

    public AStarPathPlanning(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.init();
    }


    private void init() {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<>();
            }
        };
        for (Entity next : this.worldInfo) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
            }
        }
        this.graph = neighbours;
    }


    @Override
    public List<EntityID> getResult() {
        return this.result;
    }


    @Override
    public PathPlanning setFrom(EntityID id) {
        this.from = id;
        return this;
    }


    @Override
    public PathPlanning setDestination(Collection<EntityID> targets) {
        this.targets = targets;
        return this;
    }


    @Override
    public PathPlanning precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }


    @Override
    public PathPlanning resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }


    @Override
    public PathPlanning preparate() {
        super.preparate();
        return this;
    }


    @Override
    public PathPlanning calc() {
        //待遍历的节点
        List<EntityID> open = new LinkedList<>();
        //已经遍历过的节点
        List<EntityID> close = new LinkedList<>();
        Map<EntityID, Node> nodeMap = new HashMap<>();

        //将起点加入open中
        open.add(this.from);
        nodeMap.put(this.from, new Node(null, this.from));

        while (true) {
            if (open.size() <= 0) {
                this.result = null;
                return this;
            }
            //从open中遍历出优先级最高的节点n
            Node n = null;
            for (EntityID id : open) {
                Node node = nodeMap.get(id);
                if (n == null) {
                    n = node;
                } else if (node.estimate() < n.estimate()) {
                    n = node;
                }
            }
            //如果n为终点,
            if (targets.contains(n.getID())) {
                List<EntityID> path = new LinkedList<>();
                //则从终点开始逐步追踪parent节点,一直到达起点
                while (n != null) {
                    path.add(0, n.getID());
                    n = nodeMap.get(n.getParent());
                }
                //返回找到的结果路径，算法结束
                this.result = path;
                return this;
                //如果n不是终点,
            } else {
                //将节点n从open中删除,
                open.remove(n.getID());
                //并加入到close中
                close.add(n.getID());
                //获取节点n的所有相邻节点
                Collection<EntityID> neighbours = this.graph.get(n.getID());
                //遍历节点n所有的邻近节点
                for (EntityID neighbour : neighbours) {
                    //邻近节点
                    Node m = new Node(n, neighbour);
                    //如果m即不在open也不在close,则将其添加到open中
                    if (!open.contains(neighbour) && !close.contains(neighbour)) {
                        open.add(m.getID());
                        nodeMap.put(neighbour, m);
                        //如果m在open中,并且m的estimate比原来要小,则更新其父节点
                    } else if (open.contains(neighbour) && m.estimate() < nodeMap.get(neighbour).estimate()) {
                        nodeMap.put(neighbour, m);
                        //如果m不在close中,并且m的estimate比原来要小,则更新其父节点
                    } else if (!close.contains(neighbour) && m.estimate() < nodeMap.get(neighbour).estimate()) {
                        nodeMap.put(neighbour, m);
                    }
                }
            }
        }
    }

    private class Node {

        EntityID id;
        EntityID parent;

        double cost;
        double heuristic;

        public Node(Node from, EntityID id) {
            this.id = id;

            if (from == null) {
                this.cost = 0;
            } else {
                this.parent = from.getID();
                this.cost = from.getCost() + worldInfo.getDistance(from.getID(), id);
            }

            this.heuristic = worldInfo.getDistance(id, targets.toArray(new EntityID[0])[0]);
        }


        public EntityID getID() {
            return id;
        }


        public double getCost() {
            return cost;
        }


        public double estimate() {
            return cost + heuristic;
        }


        public EntityID getParent() {
            return this.parent;
        }
    }
}