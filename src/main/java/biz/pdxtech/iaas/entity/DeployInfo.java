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

import biz.pdxtech.iaas.proto.dto.TwoParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "chaincodeId"}))
public class DeployInfo {

    public enum Stat {
        /**
         * null、准备、上传中、可部署、运行中、已停止、错误
         */
        NULL, READY, STREAMHANDLING, DEPLOYABLE, RUNNING, STOP, ERROR

    }

    @Id
    @GeneratedValue
    private Long id;
    @Column(nullable = false)
    private String fileId;//1
    @Column(nullable = false)
    private String fileName;//1
    @Column(nullable = false)
    private String fileHash;//1
    @Column(nullable = false)
    private String channel;//1
    @Column(nullable = false)
    private String chaincodeId;//1
    @Column(nullable = false)
    private String chaincodeName;//1
    @Column(nullable = false)
    private String alias;//1
    @Column(name = "`desc`", nullable = false)
    private String desc;//1
    @Column(nullable = false)
    private String pbk;//1
    @Column
    private String reason;//2
    @Column
    private long deployTime;//2
    @Column
    Date createdAt;
    @Column
    Date updatedAt;
    @Column
    Date removedAt;

    @Column
    private String chaincodeAddress;//1
    /**
     * 1-已部署, 3-已删除, 4-已启动, 5-已停止
     */
    private int status;

    private transient List<TwoParam> nodes;


}