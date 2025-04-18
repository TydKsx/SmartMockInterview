package com.sdumagicode.backend.controller.notification;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.sdumagicode.backend.core.result.GlobalResult;
import com.sdumagicode.backend.core.result.GlobalResultGenerator;
import com.sdumagicode.backend.dto.NotificationDTO;
import com.sdumagicode.backend.entity.Notification;
import com.sdumagicode.backend.entity.User;
import com.sdumagicode.backend.service.NotificationService;
import com.sdumagicode.backend.util.UserUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 消息通知
 *
 * @author ronger
 */
@RestController
@RequestMapping("/api/v1/notification")
@RequiresPermissions(value = "user")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @GetMapping("/all")
    public GlobalResult<PageInfo<NotificationDTO>> notifications(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer rows) {
        User user = UserUtils.getCurrentUserByToken();
        PageHelper.startPage(page, rows);
        List<NotificationDTO> list = notificationService.findNotifications(user.getIdUser());
        PageInfo<NotificationDTO> pageInfo = new PageInfo<>(list);
        return GlobalResultGenerator.genSuccessResult(pageInfo);
    }

    @GetMapping("/unread")
    public GlobalResult<PageInfo<Notification>> unreadNotification(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer rows) {
        User user = UserUtils.getCurrentUserByToken();
        PageHelper.startPage(page, rows);
        List<Notification> list = notificationService.findUnreadNotifications(user.getIdUser());
        PageInfo<Notification> pageInfo = new PageInfo<>(list);
        return GlobalResultGenerator.genSuccessResult(pageInfo);
    }

    @PutMapping("/read/{id}")
    public GlobalResult read(@PathVariable Long id) {
        User user = UserUtils.getCurrentUserByToken();
        Integer result = notificationService.readNotification(id, user.getIdUser());
        if (result == 0) {
            return GlobalResultGenerator.genErrorResult("标记已读失败");
        }
        return GlobalResultGenerator.genSuccessResult("标记已读成功");
    }

    @PutMapping("/read-all")
    public GlobalResult readAll() {
        Long idUser = UserUtils.getCurrentUserByToken().getIdUser();
        Integer result = notificationService.readAllNotification(idUser);
        if (result == 0) {
            return GlobalResultGenerator.genErrorResult("标记已读失败");
        }
        return GlobalResultGenerator.genSuccessResult("标记已读成功");
    }

}
