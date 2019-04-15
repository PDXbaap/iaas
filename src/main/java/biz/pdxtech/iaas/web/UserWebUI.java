/*************************************************************************
 * Copyright (C) 2016-2019 PDX Technologies, Inc. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *************************************************************************/

package biz.pdxtech.iaas.web;

import biz.pdxtech.iaas.common.JnaConstants;
import biz.pdxtech.iaas.crypto.JWTUtil;
import biz.pdxtech.iaas.entity.Address;
import biz.pdxtech.iaas.entity.User;
import biz.pdxtech.iaas.entity.User_Address;
import biz.pdxtech.iaas.hazelcast.CacheData;
import biz.pdxtech.iaas.proto.resp.RespCode;
import biz.pdxtech.iaas.repository.*;
import biz.pdxtech.iaas.proto.resp.RespEntity;
import biz.pdxtech.iaas.rest.UserRegistry;
import biz.pdxtech.iaas.util.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping(value = "/webui/user")
public class UserWebUI {

    @Autowired
    UserRepository userRepository;
    @Autowired
    ChainRepository chainRepository;
    @Autowired
    ChainNodeRepository chainNodeRepository;
    @Autowired
    ChainOrderRepository chainOrderRepository;
    @Autowired
    UserRegistry userRegistry;
    @Autowired
    AddressRepository addressRepository;
    @Autowired
    UserAddressRepository userAddressRepository;

    @Value("${pdx.iaas.default.vcode}")
    String defaultVcode;

    @ResponseBody
    @RequestMapping("/vCode")
    public RespEntity vCode(@RequestParam(value = "phone") String phone) {
        try {
            int max = 999999;
            int min = 100000;

            Random random = new Random();
            String code = random.nextInt(max) % (max - min + 1) + min + "";

            log.info("手机短信长度:{} ", MsgUtil.SplicSms(SmsCode.REGISTER, phone, code).length());

            MsgUtil.SendSms(phone, MsgUtil.SplicSms(SmsCode.REGISTER, phone, code));

            CacheData.getInstance().getCacheData().put(phone, code, 300, TimeUnit.SECONDS);

            log.info("手机号:{}: 验证码:{} 已发送！", phone, code);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return RespEntity.success();
    }

    @ResponseBody
    @RequestMapping("/register")
    public RespEntity register(@RequestParam(value = "phone") String phone,
                               @RequestParam(value = "password") String password,
                               @RequestParam(value = "vcode") String vcode) {

        if (!vcode.equals (defaultVcode)) {
            String code = (String) CacheData.getInstance ().getCacheData ().get (phone);

            if (code == null || !code.equals (vcode)) {
                log.info ("验证码不正确 --->>{}---{}", code, vcode);
                return RespEntity.error (RespCode.VCODE_ERROR);
            }
        }
        User user = userRepository.findUserByPhone(phone);

        if (user != null) {
            log.info("手机 " + phone + " 账户已存在！");
            return RespEntity.error(RespCode.USER_IS_EXIT);
        }

        user = new User();

        String uid = UUIDUtil.getUserUUID();
        user.setUid(uid);

        user.setPassword(PasswordUtil.addSoltTypeOne(password.toUpperCase(), uid));
        user.setPhone(phone);

        user = userRepository.save(user);

        log.info("注册成功！>>> userid = " + user.getId());

        return RespEntity.success();
    }

    @ResponseBody
    @RequestMapping("/signOut")
    public RespEntity signOut(@RequestParam(value = "token") String token) {
        return RespEntity.success();
    }

    @ResponseBody
    @RequestMapping("/login")
    public RespEntity login(@RequestParam(value = "phone") String phone,
                            @RequestParam(value = "password") String password) throws JOSEException, MalformedURLException {
        Map<String, Object> result = new HashMap<>();

        User user = userRepository.findUserByPhone(phone);

        if (user == null || user.isDelete()) {
            log.info("Login failure : No users with phone {} were found", phone);
            return RespEntity.error(RespCode.LOGIN_NO_REGISTER);
        }

        if (user.getPassword().equals(PasswordUtil.addSoltTypeOne(password.toUpperCase(), user.getUid()))) {
            log.info("User:{} time:{} Login success", phone, new Date());

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().subject(JsonUtil.objToJson(user)).build();
            result.put("token", JWTUtil.sign(claimsSet, JnaConstants.key));

            log.info("user token >>" + JWTUtil.sign(claimsSet, JnaConstants.key));

            List<User_Address> addrlist = userAddressRepository.findByUserId(user.getId());
            if (addrlist != null && addrlist.size() > 0) {
                result.put("address", addrlist.get(0).getAddress().getAddr());
//                String balance = JsonRpc.getBalance(userDb.getAddress().get(0).getAddr());
                String balance = "1231.132";
                result.put("balance", balance);
            }

            return RespEntity.success(result);
        } else {
            log.info("User:{} time:{} Login failure", phone, new Date());
            return RespEntity.error(RespCode.LOGIN_ERROR);
        }
    }

    @ResponseBody
    @RequestMapping("/getUserMess")
    public RespEntity getUserMessage(@RequestParam(value = "token") String token) throws MalformedURLException {

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR);
        }

        List<User_Address> addrlist = userAddressRepository.findByUserId(user.getId());
        if (addrlist != null && addrlist.size() > 0) {
            result.put("address", addrlist.get(0).getAddress().getAddr());
//                String balance = JsonRpc.getBalance(userDb.getAddress().get(0).getAddr());
            String balance = "1231.132";
            result.put("balance", balance);
        } else {
            result.put("address", "");
            result.put("balance", "");
        }
        return RespEntity.success(result);
    }

    @ResponseBody
    @RequestMapping("/bindWallet")
    public RespEntity bindWallet(@RequestParam(value = "token") String token,
                                 @RequestParam(value = "address") String address) {

        User user = userRegistry.getTokenUser(token);

        if (user == null) {
            return RespEntity.error(RespCode.TOKEN_IS_ERROR); // token验证失败
        }
        Address addr = addressRepository.findAddressByAddr (address);
        if (addr==null){
            addr = new Address();
            addr.setAddr(address);
            addr.setCreatedAt(new Date());
            addr = addressRepository.save(addr);
        }

        List<User_Address> ualist = userAddressRepository.findByUserId(user.getId());
        User_Address user_addr;
        if (ualist!=null &&ualist.size ()>0){
            user_addr=ualist.get (0);
        }else {
             user_addr = new User_Address ();
        }
        user_addr.setAddress(addr);
        user_addr.setUser(user);
        user_addr.setCreatedAt(new Date());
        userAddressRepository.save(user_addr);
        return RespEntity.success();
    }

}
