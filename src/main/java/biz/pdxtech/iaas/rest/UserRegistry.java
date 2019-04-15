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

package biz.pdxtech.iaas.rest;

import biz.pdxtech.iaas.common.JnaConstants;
import biz.pdxtech.iaas.crypto.JWTUtil;
import biz.pdxtech.iaas.entity.User;
import biz.pdxtech.iaas.proto.resp.RespCode;
import biz.pdxtech.iaas.proto.RespEntity;
import biz.pdxtech.iaas.repository.UserRepository;
import biz.pdxtech.iaas.util.JsonUtil;
import biz.pdxtech.iaas.util.PasswordUtil;
import biz.pdxtech.iaas.util.UUIDUtil;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Objects;


@Component
@Path("/user")
public class UserRegistry {

    private static final Logger log = LoggerFactory.getLogger(UserRegistry.class);
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private UserRepository userRepository;

    public UserRegistry(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(@QueryParam("phone") String phone, @QueryParam("passWord") String passWord) throws JOSEException {
        Instant instant = Instant.now();
        User userDb = userRepository.findUserByPhone(phone);
        String uid = UUIDUtil.getUserUUID();
        RespEntity result = null;
        if (userDb == null || userDb.isDelete()) {
            log.info("Login failure : No users with phone {} were found", phone);
            result = RespEntity.error(RespCode.USER_NOT_FOUNT);
            return Response.status(result.getStatus()).entity(result).build();
        }
        if (userDb.getPassword().equals(PasswordUtil.addSoltTypeOne(passWord.toUpperCase(), userDb.getUid()))) {
            log.info("User:{} time:{} Login success", phone, instant);
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(JsonUtil.objToJson(userDb)).build();
            String signed = JWTUtil.sign(claimsSet, JnaConstants.key);
            result = RespEntity.success(signed);
        } else {
            log.info("User:{} time:{} Login failure", phone, instant);
            result = RespEntity.error(RespCode.LOGIN_USER);
        }

        return Response.status(result.getStatus()).entity(result).build();
    }


    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@QueryParam("token") String token) {

        RespEntity result = null;
        User entity = getTokenUser(token);

        User user = userRepository.save(entity);
        if (Objects.isNull(userRepository.save(user))) {

            log.info("Update user failure : id={};firstName={};lastName={};phone={};genesis={};", entity.getId(), entity.getFirstName(), entity.getLastName(), entity.getPhone(), entity.getProperties());

            result = RespEntity.error(RespCode.UPDATE_USER);
            return Response.status(result.getStatus()).entity(result).build();
        }
        log.info("Update user success : id={};firstName={};lastName={};phone={};genesis={};", entity.getId(), entity.getFirstName(), entity.getLastName(), entity.getPhone(), entity.getProperties());

        result = RespEntity.success(user);

        return Response.status(result.getStatus()).entity(result).build();
    }

    @DELETE
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@QueryParam("token") String token) {

        RespEntity result = null;
        User entity = getTokenUser(token);

        if (!userRepository.deleteUserByPhone(entity.getFirstName())) {

            log.info("Delete user failure : phone={};", entity.getPhone());

            result = RespEntity.error(RespCode.DELETE_USER);
            return Response.status(result.getStatus()).entity(result).build();
        }
        log.info("Delete user success : phone={};", entity.getPhone());

        result = RespEntity.success(null);

        return Response.status(result.getStatus()).entity(result).build();
    }


    @GET
    @Path("/retrieve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response retrieve(@QueryParam("token") String token) {

        RespEntity result = null;
        User entity = getTokenUser(token);
        User user = userRepository.findUserByPhone(entity.getPhone());
        if (user == null) {
            result = RespEntity.error(RespCode.DELETE_USER);
            return Response.status(result.getStatus()).entity(result).build();
        }
        result = RespEntity.success(user);
        return Response.status(result.getStatus()).entity(result).build();

    }


    @GET
    @Path("/listall")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response listall() {

        RespEntity result = null;

        List<User> userList = userRepository.findAll();
        if (userList == null) {
            result = RespEntity.error(RespCode.DELETE_USER);

            return Response.status(result.getStatus()).entity(result).build();
        }
        result = RespEntity.success(userList);

        return Response.status(result.getStatus()).entity(result).build();

    }

    public User getTokenUser(String token) {
        JWTClaimsSet jwtClaimsSet = null;
        try {
            jwtClaimsSet = JWTUtil.verify(token, JnaConstants.key.toPublicJWK());
            return JsonUtil.jsonToObj(jwtClaimsSet.getSubject(), User.class);
        } catch (Exception e) {
            log.info("token获取用户失败！");
            return null;
        }
    }

}
