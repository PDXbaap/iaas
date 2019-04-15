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

package biz.pdxtech.iaas.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
public class ChainOrder implements Serializable {

    public enum Stat {
        //状态：待支付，支付完成，支付失败，超时支付
        DEF_PAY, PAY_FINISH, PAY_FAIL, PAY_TIMEOUT;
    }

    public enum Type {
        //状态：创建  续费
        CREATE, RENEW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private String uuid;

    @OneToOne
    private Chain chain;

    @ManyToOne
    private User user;

    private Stat stat;

    private Type type; // 类型：0创建  1 续费

    private int feeMode; // 计费方式（月）

    private double confFee; // 配置费用

    private String fromAddr; // 支付地址

    private String toAddr; // 收款地址

    private Date createTime;

    private Date updateTime;

}
