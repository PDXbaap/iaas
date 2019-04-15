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

package biz.pdxtech.iaas.amqp;

import biz.pdxtech.iaas.service.impl.TopologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class Receiver {

    private static final Logger log = Logger.getLogger(Receiver.class.getName());

    @Autowired
    TopologyService topoService;

    public void handleMessage(QueMessage msg) {

        log.info("Received <" + "type:" + msg.getType() + ">");

        switch (msg.getType()) {

            case NODE_REGISTERED:

                break;

            case NODE_INVALIDATED:

                break;

            case CHAIN_BLOCK:

                break;

            case HEARTBEAT:

                log.info("Received <" + "type:" + msg.getType() + ">");

                break;

            default:

                log.info("Received <" + "type:" + msg.getType() + ", not interested, hence ignored>");

                break;
        }

    }
}
