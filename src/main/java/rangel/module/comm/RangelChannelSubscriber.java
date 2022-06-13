package rangel.module.comm;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.component.communication.ChannelSubscriber;
import org.jetbrains.annotations.NotNull;
import rescuecore2.standard.entities.StandardEntityURN;

import java.util.Objects;

/**
 * 频道订阅者
 *
 * @author 软工20-2金磊
 */
public class RangelChannelSubscriber extends ChannelSubscriber {

    /**
     * 获得频道号
     *
     * @param agentType    智能体类型
     * @param channelIndex 频道的索引
     * @param numChannels  频道数量
     * @return int 频道号
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    public static int getChannelNumber(StandardEntityURN agentType, int channelIndex, int numChannels) {
        int agentIndex = 0;
        //如果是消防队或者消防站
        if (agentType == StandardEntityURN.FIRE_BRIGADE || agentType == StandardEntityURN.FIRE_STATION) {
            agentIndex = 1;
            //如果是警察或者警察局
        } else if (agentType == StandardEntityURN.POLICE_FORCE || agentType == StandardEntityURN.POLICE_OFFICE) {
            agentIndex = 2;
            //如果是救护队或者救护中心
        } else if (agentType == StandardEntityURN.AMBULANCE_TEAM || agentType == StandardEntityURN.AMBULANCE_CENTRE) {
            agentIndex = 3;
        }

        //根据智能体类型的不同分配不同的频道号
        int index = (3 * channelIndex) + agentIndex;
        if ((index % numChannels) == 0) {
            index = numChannels;
        } else {
            index = index % numChannels;
        }
        return index;
    }

    /**
     * 判断是否是排智能体
     *
     * @param agentInfo 智能体信息
     * @param worldInfo 世界信息
     * @return boolean (true是||false不是)
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected boolean isPlatoonAgent(AgentInfo agentInfo, WorldInfo worldInfo) {
        StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);
        //如果是消防队,警察或者救护队返回true
        return agentType == StandardEntityURN.FIRE_BRIGADE
                || agentType == StandardEntityURN.POLICE_FORCE
                || agentType == StandardEntityURN.AMBULANCE_TEAM;
    }

    /**
     * 获得智能体的类型
     *
     * @param agentInfo 智能体信息
     * @param worldInfo 世界信息
     * @return rescuecore2.standard.entities.StandardEntityURN 标准实体URN
     * @author 软工20-2金磊
     * @since 2022/6/13
     */
    protected StandardEntityURN getAgentType(@NotNull AgentInfo agentInfo, @NotNull WorldInfo worldInfo) {
        return Objects.requireNonNull(worldInfo.getEntity(agentInfo.getID())).getStandardURN();
    }

    public static void main(String[] args) {
        int numChannels = 6;
        int maxChannels = 2;
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("FIREBRIGADE-" + i + ":" + getChannelNumber(StandardEntityURN.FIRE_BRIGADE, i, numChannels));
        }
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("POLICE-" + i + ":" + getChannelNumber(StandardEntityURN.POLICE_OFFICE, i, numChannels));
        }
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("AMB-" + i + ":" + getChannelNumber(StandardEntityURN.AMBULANCE_CENTRE, i, numChannels));
        }
    }

    @Override
    public void subscribe(@NotNull AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, MessageManager messageManager) {
        // 一开始只订阅一次
        if (agentInfo.getTime() == 1) {
            //第0个频道是语音频道
            int numChannels = scenarioInfo.getCommsChannelsCount() - 1;
            //最大频道数
            int maxChannelCount;
            //智能体是否是排
            boolean isPlatoon = isPlatoonAgent(agentInfo, worldInfo);
            if (isPlatoon) {
                maxChannelCount = scenarioInfo.getCommsChannelsMaxPlatoon();
            } else {
                maxChannelCount = scenarioInfo.getCommsChannelsMaxOffice();
            }

            //获得智能体类型
            StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);
            //计算所有的频道号
            int[] channels = new int[maxChannelCount];
            for (int i = 0; i < maxChannelCount; i++) {
                channels[i] = getChannelNumber(agentType, i, numChannels);
            }

            //订阅频道
            messageManager.subscribeToChannels(channels);
        }
    }
}