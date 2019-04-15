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
import java.util.Date;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Chain {

    public enum Type {
        //链类型：信任链，服务链，业务链
        TRUST_CHAIN, SERVICE_CHAIN, BIZ_CHAIN
    }

    public enum BCtype {
        //业务链类型：共有链，联盟链，其它链
        PUBLIC, PERMIT, OTHER
    }

    public enum Stat {
        //状态：准备中, 服务中, 已移除, 失败
        NOT_READY, IN_SERVICE, REMOVED, FAIL
    }

    public enum Stack {
        //协议栈：ethereum，fabric，pdx
        PDX, ETHEREUM, FABRIC
    }

    public enum Consensus {
        //共识算法：pow，pos
        POW, POS
    }

    public enum Intensity {
        //信任强度：全生态信任背书，单链信任背书
        TOTAL_ECOLOGY, SINGLE_CHAIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long chainId;

    private String name;

    private Long part;

    private Type type;

    private BCtype bcType;

    private Stat stat;

    /**
     * 性能TPS
     */
    private Integer tps;

    /**
     * 是否公开
     */
    private Boolean open;

    private Consensus consensus;

    private Intensity intensity;

    private Stack stack;

    /**
     * the number of chains currently serving
     */
    private Integer sizeChain;

    /**
     * lower Trust Chain for sending trustTX
     */
    private Long lowerChainId;

    //最后存活期限
    private Long deadline;

    @OneToOne
    Address owner;

    // the numebr of nodes owner has bought or requested
    private Integer sizeDesired;

    // the number of nodes currently serving the chain
    // must corresponding number of Chain-Node records
    private Integer sizeService;

    // 共享链
    private Long shareChainId;

    // 是否为岛屿状态
    private Boolean islandState;

    // commit block 区间
    private Integer cfd;

    // 打块时间
    private Integer blockDelay;

    // 区块高度
    private Long commitHeight;

    // 创世文件
    @Lob
    private String genesis;

    // 指派记录
    @Lob
    private String assignRecord;

    private String imgUrl;

    /**
     * java genesis in json, e.g. {"k-1":"v-1","k-2":"v-2"}
     */
    @Lob
    private String properties;

    private Date createdAt;

}