package cn.iocoder.yudao.module.video.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DouyinPublishStatusEnum {

    UNPUBLISHED(0, "未发布"),
    PUBLISHING(1, "发布中"),
    PUBLISHED(2, "已发布"),
    FAILED(3, "发布失败");

    private final Integer status;
    private final String name;

}
