package com.sdumagicode.backend.dto;

import lombok.Data;

/**
 * @author ronger
 */
@Data
public class AnswerDTO {

    private Integer idSubjectQuestion;

    private String answer;

    private Integer idUser;

}
