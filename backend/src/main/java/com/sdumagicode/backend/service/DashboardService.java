package com.sdumagicode.backend.service;

import com.sdumagicode.backend.dto.ArticleDTO;
import com.sdumagicode.backend.dto.BankAccountDTO;
import com.sdumagicode.backend.dto.UserInfoDTO;
import com.sdumagicode.backend.dto.admin.Dashboard;

import java.util.List;
import java.util.Map;

/**
 * @author ronger
 */
public interface DashboardService {

    /**
     * 统计系统数据
     *
     * @return
     */
    Dashboard dashboard();

    /**
     * 统计最近三十天数据
     *
     * @return
     */
    Map lastThirtyDaysData();

    /**
     * 获取历史数据
     *
     * @return
     */
    Map history();

    /**
     * 获取新增用户列表
     *
     * @return
     */
    List<UserInfoDTO> newUsers();

    /**
     * 获取新增银行账号列表
     *
     * @return
     */
    List<BankAccountDTO> newBankAccounts();

    /**
     * 获取新增文章列表
     *
     * @return
     */
    List<ArticleDTO> newArticles();
}
