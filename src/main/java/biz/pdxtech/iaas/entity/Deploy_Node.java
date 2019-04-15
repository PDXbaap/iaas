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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Arrays;
import java.util.Date;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"deployInfoId", "node_id"}))
public class Deploy_Node {

    public enum Stat {
        /**
         * null、准备、上传中、可部署、运行中、已停止、错误、已移除
         */
        NULL, READY, STREAMHANDLING, DELETED, RUNNING, STOP, ERROR, REMOVED

    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @ManyToOne
    @JoinColumn(name = "deployInfoId")
    DeployInfo deployInfo;

    @OneToOne
    Node node;

    @OneToOne
    Chain chain;

    private Stat stat;

    private Date creatAt;

    private Date updateAt;

    private Date removeAt;


}
