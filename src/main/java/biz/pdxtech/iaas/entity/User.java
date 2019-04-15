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

import javax.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @OneToOne
    Role role;

    @Column(nullable = true, unique = true)
    private String uid;

    @Column(nullable = true, unique = true)
    private String phone;

    @Column(nullable = true)
    private String firstName;

    @Column(nullable = true)
    private String lastName;

    @Column(nullable = true)
    private String password;

    @Column(nullable = true)
    private boolean isDelete;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto0;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto1;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto2;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto3;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto4;

    @Column(nullable = true)
    @Lob
    private byte[] idPhoto5;

    @Lob
    String properties; // java genesis in json, e.g. {"k-1":"v-1","k-2":"v-2"}
}

