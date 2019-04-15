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

package biz.pdxtech.iaas.repository;

import biz.pdxtech.iaas.entity.DeployInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeployInfoRepository extends JpaRepository<DeployInfo, Long> {

    @Query(value = "UPDATE deploy_info SET `status`= :status , deploy_time= :deployTime ,reason= :reason WHERE id= :id", nativeQuery = true)
    @Modifying
    void updateDeployStatus(@Param("status") int status, @Param("deployTime") long deployTime, @Param("reason") String reason, @Param("id") long id);

    List<DeployInfo> findAllByPbkEqualsOrderByIdDesc(String pbk);

    List<DeployInfo> findDeployInfosByChannelIs(String channel);

    List<DeployInfo> findAllByPbkAndChannelOrderByIdDesc(String pbk, String channel);

    List<DeployInfo> findAllByPbkOrderByIdDesc(String pbk);

    DeployInfo findByChaincodeAddressAndChannel(String chainnodeAddress, String channel);

    DeployInfo findByChaincodeIdAndChannel(String chainnodeId, String channel);

    DeployInfo findDeployInfoById(Long id);

    void deleteAllByIdIn(List<Long> ids);
}
