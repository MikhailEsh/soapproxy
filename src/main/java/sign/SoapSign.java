package sign;


import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSEncryptionPart;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.components.crypto.Merlin;
import org.apache.ws.security.message.WSSecHeader;
import org.apache.ws.security.message.WSSecSignature;
import org.apache.ws.security.message.WSSecTimestamp;
import org.w3c.dom.Document;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SoapSign {

    private String alias = "xws-security-client";
    private String passphrase;
    private File ksFile;

    public SoapSign(String passphrase) {
        this.passphrase = passphrase;
        this.ksFile = new File(System.getProperty("user.dir") + "/src/main/resources/client-keystore.jks");
    }

    public String signSoapMessage(String message) throws Exception {
        final int signatureValidityTime = 3600; // 1hour in seconds

        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(new FileInputStream(ksFile), passphrase.toCharArray());
        X509Certificate cert = (X509Certificate) keystore.getCertificate(alias);

        WSSConfig config = new WSSConfig();
        config.setWsiBSPCompliant(false);
        WSSecSignature builder = new WSSecSignature(config);

        builder.setX509Certificate(cert);
        builder.setUserInfo(alias, passphrase);
        builder.setUseSingleCertificate(true);
        builder.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);


        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage soapMessage = mf.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();
        InputStream is = new ByteArrayInputStream(message.getBytes());
        soapPart.setContent(new StreamSource(is));
        Document document = soapMessage.getSOAPPart().getEnvelope().getOwnerDocument();

        WSSecHeader secHeader = new WSSecHeader();
        secHeader.setMustUnderstand(true);
        secHeader.insertSecurityHeader(document);

        WSSecTimestamp timestamp = new WSSecTimestamp();
        timestamp.setTimeToLive(signatureValidityTime);
        document = timestamp.build(document, secHeader);


        List<WSEncryptionPart> parts = new ArrayList<>();
        WSEncryptionPart timestampPart = new WSEncryptionPart("Timestamp", WSConstants.WSU_NS, "");
        WSEncryptionPart bodyPart = new WSEncryptionPart(WSConstants.ELEM_BODY, WSConstants.URI_SOAP11_ENV, "");
        parts.add(timestampPart);
        parts.add(bodyPart);
        builder.setParts(parts);

        Properties properties = new Properties();
        properties.setProperty("org.apache.ws.security.crypto.provider", "org.apache.ws.security.components.crypto.Merlin");
        Crypto crypto = CryptoFactory.getInstance(properties);
        ((Merlin) crypto).setKeyStore(keystore);

        crypto.loadCertificate(new ByteArrayInputStream(cert.getEncoded()));

        document = builder.build(document, crypto, secHeader);
        return documentToString(document);
    }

    private String documentToString(Document doc) throws TransformerException {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(domSource, result);
        return writer.toString();
    }

}

