package com.sdumagicode.backend.dto;

import lombok.Data;

/**
 * @author ronger
 */
@Data
public class ArticleSearchDTO {

    private String searchText;

    private String topicUri;

    private String tag;

}
