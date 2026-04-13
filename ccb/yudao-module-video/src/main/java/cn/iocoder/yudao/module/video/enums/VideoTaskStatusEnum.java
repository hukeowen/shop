package cn.iocoder.yudao.module.video.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VideoTaskStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(1, "生成中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final Integer status;
    private final String name;

}
