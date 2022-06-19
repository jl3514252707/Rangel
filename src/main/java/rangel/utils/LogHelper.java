package rangel.utils;

import adf.core.agent.info.AgentInfo;
import org.apache.log4j.Logger;

/**
 * 日志帮助类,可以调用该类输出动作的详细日志
 *
 * @author 软工20-2金磊
 */
public class LogHelper {

    /**
     * 使用log4j作为日志框架
     */
    private final Logger logger;

    /**
     * 使用该日志的智能体信息
     */
    private AgentInfo agentInfo;

    /**
     * 是否开启日志
     */
    private static final boolean isOpen = PropertiesUtils.getBoolean("other.properties", "isOpen", false);


    public LogHelper(String agent) {
        logger = Logger.getLogger(agent);
    }

    /**
     * 设置智能体信息
     *
     * @param agentInfo 智能体信息
     * @author 软工20-2金磊
     * @since 2022/6/18
     */
    public void setAgentInfo(AgentInfo agentInfo) {
        this.agentInfo = agentInfo;
    }

    /**
     * 调用log4j的info级别的日志信息输出
     *
     * @param message 想要输出的日志信息
     * @author 软工20-2金磊
     * @since 2022/6/18
     */
    public void info(String message) {
        if (isOpen) {
            message = "[第" + agentInfo.getTime() + "回合][" + agentInfo.me().getID() + "号智能体]:" + message;
            logger.info(message);
        }
    }
}
