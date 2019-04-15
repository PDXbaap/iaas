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

package biz.pdxtech.iaas.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
public class Partitioner {

    private String self; //my identifier in the cluster

    private int rank; //my rank in the cluster

    private long size; //size of the cluster

    private long sect; //section width, 2**32 / size

    private static final Logger log = LoggerFactory.getLogger(Partitioner.class);

    public void setSelf(String self) {
        this.self = self;
    }

    public void adjust(List<String> members, ClusterRunner runner) {

        log.info("auto adjust >>>>>, cluster size = {}", members.size());

        java.util.Collections.sort(members);

        log.info("members: {}", members.toString());

        this.size = members.size();
//        this.sect = 0xFFFFFFFF / this.size;
        this.sect = (1L << 32) / this.size;


        this.rank = 0;
        for (String member : members) {
            if (self.equalsIgnoreCase(member)) {
                break;
            }
            this.rank++;
        }

        log.info("auto adjust <<<<<, my rank is {}", this.rank);
    }


    public long getPartitionLWM() {
        return this.rank * this.sect;
    }

    public long getPartitionHWM() {
        return (this.rank + 1) * this.sect;
    }


    /**
     * Given a request identifier and the size of the cluster, return my rank
     * in priority of processing this request.
     *
     * @param name
     * @return my rank of priority on processing this request
     */
    public long rank(String name) {

        if (name == null || name.length() == 0) return 0;

        int bkt = (int) (name2part(name) / this.sect); // this work falls into the ith bucket

        if (this.rank >= bkt) return this.rank - bkt;

        return this.size - (bkt - this.rank);
    }

    public static long name2part(String name) {

        MessageDigest md = null;
        try {

            byte[] hash = MessageDigest.getInstance("MD5").digest(name.getBytes());

            hash[0] = hash[1] = hash[2] = hash[3] = 0;

            ByteBuffer bb = ByteBuffer.wrap(hash);

            return bb.getLong();

        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }
}
