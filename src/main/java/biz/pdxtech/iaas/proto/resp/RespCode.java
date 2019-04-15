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

package biz.pdxtech.iaas.proto.resp;


public enum RespCode {

    //ChainRegistry Error
    CHAIN_CREATE_ERROR(1001, "Chain创建失败"),
    //NodeRegistry Error
    NODE_EXAMPLE(2001, "Node注册中心错误示例"),
    //CREATE_USER Error
    CREATE_USER(3001, "USER创建失败"),
    //UPDATE_USER Error
    UPDATE_USER(3002, "USER更新失败"),
    //DELETE_USER Error
    DELETE_USER(3003, "USER删除失败"),
    //DELETE_USER Error
    LOGIN_USER(3004, "USER登录失败"),
    //DELETE_USER Error
    USER_NOT_FOUNT(3005, "账户不存在"),
    USER_IS_EXIT(3006, "用户已注册"),
    LOGIN_ERROR(3007, "用户名密码错误"),
    USER_NOT_WALLET(3008, "用户未绑定钱包"),
    ORDER_NOT_USER(3009, "用户订单不存在"),
    TOKEN_IS_ERROR(3010, "token失效,请重新登录"),
    VCODE_ERROR(3011, "短信验证码错误！"),
    CHAIN_NAME_ERROR(3012, "链名称已存在！"),
    LOGIN_NO_REGISTER(3013, "该手机号未注册"),
    WALLET_NO_NULL(3014, "钱包地址已绑定"),
    BALANCE_IS_LESS(3015, "钱包账户余额不足"),
    USER_EXIT_WALLET(3016, "用户已绑定钱包"),
    DEF_PAY_ORDER(3017, "存在未支付的订单"),
    ASSET_NAME_EXSIST(3018, "token名称已经存在"),
    NODE_NUMBER_HIGHT(3019, "节点数量不足,请重新输入节点数"),


    //DappRegistry Error
    DAPP_EXAMPLE(4001, "Dapp注册中心错误示例");


    private int errorCode;
    private String errorMsg;

    RespCode(int errorCode, String errorMsg) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg() {
        return this.errorMsg;
    }
}
