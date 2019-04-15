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

package biz.pdxtech.iaas.crypto;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;

import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JWTUtil {

    private static final Logger log = Logger.getLogger(JWTUtil.class.getName());

    public static com.nimbusds.jose.jwk.ECKey keyGen(String keyID) {
        try {
            return new ECKeyGenerator(Curve.P_256K).keyID(keyID).generate();
        } catch (JOSEException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static com.nimbusds.jose.jwk.ECKey pubK(com.nimbusds.jose.jwk.ECKey key) {
        return key.toPublicJWK();
    }

    public static String sign(JWTClaimsSet claimSet, com.nimbusds.jose.jwk.ECKey priK) throws JOSEException {
        // Create JWT for ES256K alg
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256K).keyID(priK.getKeyID()).build(),
                claimSet);

        // Sign with private EC key
        jwt.sign(new ECDSASigner(priK));

        return jwt.serialize();
    }

    public static JWTClaimsSet verify(String token, com.nimbusds.jose.jwk.ECKey pubK) throws ParseException, JOSEException {

        // Parse signed JWT
        SignedJWT jwt = SignedJWT.parse(token);

        // Verify the ES256K signature with the public EC key
        boolean ok = jwt.verify(new ECDSAVerifier(pubK));

        if (ok) {
            return jwt.getJWTClaimsSet();
        }

        return null;
    }

    public static void main(String[] args) {

        try {
            com.nimbusds.jose.jwk.ECKey key = JWTUtil.keyGen("jz");

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("alice").build();

            String signed = JWTUtil.sign(claimsSet, key);

            log.info(signed);

            JWTClaimsSet claimsSet2;
            try {
                claimsSet2 = JWTUtil.verify(signed, key.toPublicJWK());
                log.info(claimsSet2.getSubject());

            } catch (ParseException ex) {
                Logger.getLogger(JWTUtil.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (JOSEException ex) {
            Logger.getLogger(JWTUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
