package com.AFF.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.AFF.dto.UserDTO;
import com.AFF.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.AFF.dto.LoginFormDTO;
import com.AFF.dto.Result;
import com.AFF.entity.User;
import com.AFF.mapper.UserMapper;
import com.AFF.service.IUserService;
import com.AFF.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.AFF.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author UMBRELLAZG
 * @since 2024-11-21
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //符合则生成验证
        String code = RandomUtil.randomNumbers(6);
//        //保存到sesssion
//        session.setAttribute("code",code);
        //保存到Redis，并设置过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证验证码
        log.debug("发送验证码成功，验证码为：{}", code);
        //返回结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验验证码和手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
//        Object cacheCode = session.getAttribute("code");
        Object cacheCode =stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code= loginForm.getCode();
        if(cacheCode == null||!cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }
        //根据手机号查用户是否存在
        User user = query().eq("phone", phone).one();
        //如果用户不存在则创建新用户
        if (user == null){
            user = creatUserWithPhone(phone);
        }

//        //用户保存到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //保存到Redis
        //随机生成token作为用户登录凭证
        String token= UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将user对象转为hash存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setFieldValueEditor(
                        (fieldname,fieldvalue)->fieldvalue.toString()));
        String tokenKey = LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //存储


        return Result.ok();
    }

    @Override
    public Result sign() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId+keySuffix;
        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入Redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        //拼接key
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId+keySuffix;
        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截止今天的所有签到记录
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().
                        get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num=result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }
        //循环遍历
        int cnt=0;
        while(true){
        //让这个数字与1做与运算，得到数字的最后一个bit
            if((num & 1)==0){
                break;
            }else {
                cnt++;
                //如果为1，说明已签到，计数器+1
            }
            num >>>= 1;
        }
            return Result.ok(cnt);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("ASKFF_USER_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
