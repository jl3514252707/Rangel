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

    /**
     * 地图上每个区域的邻居节点的Map
     */
    private Map<EntityID, Set<EntityID>> allNeighbors;

    /**
     * 起点
     */
    private EntityID from;

    /**
     * 目标
     */
    private Collection<EntityID> targets;

    /**
     * 计算出的路径
     */
    private List<EntityID> result;

    public AStarPathPlanning(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.init();
    }


    /**
     * 初始化,计算地图上每个区域的邻居的节点
     *
     * @author 软工20-2金磊
     * @since 2022/6/20
     */
    private void init() {
        Map<EntityID, Set<EntityID>> allNeighbors = new LazyMap<>() {
            @Override
            public Set<EntityID> createValue() {
                return new HashSet<>();
            }
        };
        //遍历出地图中所有的实体
        for (Entity entity : this.worldInfo) {
            //如果该实体是区域,
            if (entity instanceof Area area) {
                //则获取其所有的邻居集合
                Collection<EntityID> areaNeighbours = area.getNeighbours();
                allNeighbors.get(entity.getID()).addAll(areaNeighbours);
            }
        }
        this.allNeighbors = allNeighbors;
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
                } else if (node.getF() < n.getF()) {
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
                Collection<EntityID> neighbours = this.allNeighbors.get(n.getID());
                //遍历节点n所有的邻近节点
                for (EntityID neighbour : neighbours) {
                    //邻近节点
                    Node m = new Node(n, neighbour);
                    //如果m即不在open也不在close,则将其添加到open中
                    if (!open.contains(neighbour) && !close.contains(neighbour)) {
                        open.add(m.getID());
                        nodeMap.put(neighbour, m);
                        //如果m在open中,并且m的estimate比原来要小,则更新其父节点
                    } else if (open.contains(neighbour) && m.getF() < nodeMap.get(neighbour).getF()) {
                        nodeMap.put(neighbour, m);
                        //如果m不在close中,并且m的estimate比原来要小,则更新其父节点
                    } else if (!close.contains(neighbour) && m.getF() < nodeMap.get(neighbour).getF()) {
                        nodeMap.put(neighbour, m);
                    }
                }
            }
        }
    }


    private class Node {

        /**
         * 当前节点的实体ID
         */
        private final EntityID id;

        /**
         * 父节点的实体ID
         */
        private final EntityID parent;

        /**
         * G评分-根据当前点与起始点的距离
         */
        private final double g;

        /**
         * H评分-根据当前点与目标点的距离
         */
        private final double h;

        public Node(Node from, EntityID id) {
            this.id = id;

            //如果父节点为null,说明该点是起始点,G为0
            if (from == null) {
                this.parent = null;
                this.g = 0;
            } else {
                this.parent = from.getID();
                this.g = from.getG() + worldInfo.getDistance(from.getID(), id);
            }

            this.h = worldInfo.getDistance(id, (EntityID) targets.toArray()[0]);
        }


        /**
         * 获得本节点的实体ID
         *
         * @return rescuecore2.worldmodel.EntityID 实体ID
         * @author 软工20-2金磊
         * @since 2022/6/20
         */
        public EntityID getID() {
            return id;
        }


        /**
         * 获得父节点的实体ID
         *
         * @return rescuecore2.worldmodel.EntityID 实体ID
         * @author 软工20-2金磊
         * @since 2022/6/20
         */
        public EntityID getParent() {
            return this.parent;
        }


        /**
         * 获取当前点与起始点间的距离
         *
         * @return double G
         * @author 软工20-2金磊
         * @since 2022/6/20
         */
        public double getG() {
            return g;
        }


        /**
         * 获取总评F,F=G+H,F越小,代表该节点的优先级越大
         *
         * @return double F
         * @author 软工20-2金磊
         * @since 2022/6/20
         */
        public double getF() {
            return g + h;
        }
    }
}