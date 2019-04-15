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

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
public class Node {

    public enum Stat {
        //状态：准备中，服务中，不可控，已移除
        NOT_READY, IN_SERVICE, UNCONTROLLED, REMOVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    private Address addr;

    @OneToOne
    private Address owner;

    private Stat stat;

    private Integer sizeService;

    private Long part;

    private String ip;

    private String enode;

    private String nodeKey;

    private String minerKey;

    /**
     * 节点位置
     */
    private String position;

    /**
     * 是否个人独占
     */
    private Boolean personal;

    /**
     * java genesis in json, e.g. {"k-1":"v-1","k-2":"v-2"}
     */
    @Lob
    private String properties;

}
