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

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CryptoUtil {

    public static byte[] sign(PrivateKey privateKey, byte[] msg) throws Exception {
        Signature signature = Signature.getInstance("SHA3-256withECDSA");
        signature.initSign(privateKey);

        signature.update(msg);

        return signature.sign();
    }

    public static boolean verify(PublicKey publicKey, byte[] signed, byte[] msg) throws Exception {
        Signature signature = Signature.getInstance("SHA3-256withECDSA");
        signature.initVerify(publicKey);

        signature.update(msg);

        return signature.verify(signed);
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("ECDSA", "BC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    public static void main(String[] args) throws NoSuchProviderException, InvalidAlgorithmParameterException, Exception {

        try {

            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

            KeyPair pair = generateKeyPair();
            PrivateKey priK = pair.getPrivate();
            PublicKey pubK = pair.getPublic();

            String msg = "This is string to sign";

            byte[] sig = sign(priK, msg.getBytes());

            boolean verified = verify(pubK, sig, msg.getBytes());

            System.out.println(verified);


            Properties props = new Properties();
            props.setProperty("a", "1");
            props.setProperty("b", "2");

            AuthToken token = AuthToken.builder().addr("jz").meta(props).salt("aaa".getBytes()).timestamp(System.currentTimeMillis() / 1000).build();

            String tstr = token.sign(priK);

            AuthToken token2 = AuthToken.verify(pubK, tstr);

            System.out.println(token2.addr);

        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(AuthToken.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(AuthToken.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(AuthToken.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SignatureException ex) {
            Logger.getLogger(AuthToken.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
