package com.prepdeck.interview.enums;

import lombok.Getter;

@Getter
public enum CompanyEnum {

    BYTE_DANCE("字节跳动", "强调算法、高并发、快速迭代，追问多且尖锐",
            "张明", "技术专家", "字节跳动推荐系统负责人，8年算法工程经验", "直爽果断，喜欢追问底层实现，对模糊回答零容忍"),
    ALIBABA("阿里巴巴", "强调项目深度、业务理解、系统设计，关注为什么",
            "王浩", "高级技术专家", "阿里云分布式系统出身，主导过多次双11架构升级", "沉稳严谨，注重逻辑闭环，常问「你为什么要这么做」"),
    TENCENT("腾讯", "强调基础知识扎实、工程能力、产品思维",
            "李薇", "技术总监", "腾讯产品技术部出身，从工程师到管理者的完整路径", "温和但专业，关注候选人成长潜力，会追问「如果重新做你会怎么做」"),
    BAIDU("百度", "强调技术原理、搜索/AI相关、逻辑严谨",
            "赵博士", "首席工程师", "百度搜索核心团队出身，拥有多项AI相关专利", "学术严谨，喜欢从原理出发层层深入，不满足于表层答案"),
    MEITUAN("美团", "强调业务落地、分布式系统、数据驱动",
            "刘强", "架构师", "美团到家业务技术架构负责人，主导过百万级QPS系统设计", "务实高效，关注方案的可执行性，常问「这个方案落地遇到什么困难」"),
    PDD("拼多多", "强调执行力、极致性能、业务导向",
            "陈刚", "技术VP", "拼多多技术团队负责人，从零搭建过多个核心业务系统", "直接高效，讨厌空谈，对性能优化有执念，喜欢挑战候选人的极限");

    private final String displayName;
    private final String styleDescription;
    private final String interviewerName;
    private final String title;
    private final String bio;
    private final String personality;

    CompanyEnum(String displayName, String styleDescription,
                String interviewerName, String title, String bio, String personality) {
        this.displayName = displayName;
        this.styleDescription = styleDescription;
        this.interviewerName = interviewerName;
        this.title = title;
        this.bio = bio;
        this.personality = personality;
    }
}
