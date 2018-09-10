/* 
 * Copyright (C) 2018 Tribein
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
 */
package weblogicinfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import javax.management.remote.JMXServiceURL;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import java.util.Hashtable;
import java.lang.String;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import static javax.management.remote.JMXConnectorFactory.PROTOCOL_PROVIDER_PACKAGES;
import static javax.management.remote.JMXConnectorFactory.connect;
import static javax.naming.Context.SECURITY_CREDENTIALS;
import static javax.naming.Context.SECURITY_PRINCIPAL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class InfoCollector extends Thread {

    MBeanServerConnection remote = null;
    JMXConnector connector = null;
    MBeanInfo info;
    MBeanAttributeInfo[] attributes;
    Hashtable env = new Hashtable();
    Set<ObjectName> mbeans = new HashSet<>();
    JMXServiceURL serviceURL;
    String username;
    String password;
    ObjectName domainMBean;
    ObjectName service;
    String adminServerName;
    Document outDoc;
    Element rootOutElement;
    MessageDigest md;
    Map<String, String> mapps = new TreeMap();
    Map<String, String> mds = new TreeMap();
    Map<String, String> msrv = new TreeMap();
    Map<String, String> mnm = new TreeMap();
    Map<String, String> mclst = new TreeMap();
    Map<String, String> elMap = new TreeMap();
    PrintWriter threadOut;
    String md5hash;

    public InfoCollector(String adminServerHost, int adminServerPort, String wlUser, String wlPassword, PrintWriter out) {
        try {
            serviceURL = new JMXServiceURL(
                    "t3",
                    adminServerHost,
                    adminServerPort,
                    "/jndi/" + "weblogic.management.mbeanservers.domainruntime"
            );
            service = new ObjectName("com.bea:Name=DomainRuntimeService,Type=weblogic.management.mbeanservers.domainruntime.DomainRuntimeServiceMBean");

        } catch (Exception e) {
        }
        username = wlUser;
        password = wlPassword;
        threadOut = out;
    }

    private void processWLHome() {
        try {
            adminServerName = (String) remote.getAttribute(service, "ServerName");
            ObjectName serviceName = new ObjectName("com.bea:Name=" + adminServerName + ",Location=" + adminServerName + ",Type=ServerRuntime");
            addXMLWlHome(remote.getAttribute(serviceName, "WeblogicHome").toString());
        } catch (Exception e) {

        }
    }

    private void processWLVersion() {
        try {
            addXMLWlDomainVersion(remote.getAttribute(domainMBean, "DomainVersion").toString());
        } catch (Exception e) {

        }
    }

    private void processDomain() {
        //String domainName;
        try {
            //domainName = remote.getAttribute(domainMBean, "Name").toString();
            elMap.clear();
            elMap.put("NAME", remote.getAttribute(domainMBean, "Name").toString());
            elMap.put("ROOTDIRECTORY", remote.getAttribute(domainMBean, "RootDirectory").toString());
            addXMLDomain(elMap);
            elMap.clear();
        } catch (Exception e) {
        }
    }

    private void processJavaVersion() {
        ObjectName javaName;
        try {
            try {
                javaName = new ObjectName("com.bea:ServerRuntime=" + adminServerName + ",Name=" + adminServerName + ",Location=" + adminServerName + ",Type=JVMRuntime");
                addXMLJavaVersion(remote.getAttribute(javaName, "JavaVersion").toString());
            } catch (Exception e) {
                javaName = new ObjectName("com.bea:ServerRuntime=" + adminServerName + ",Name=" + adminServerName + ",Location=" + adminServerName + ",Type=JRockitRuntime");
                addXMLJavaVersion(remote.getAttribute(javaName, "JavaVersion").toString());
            }
        } catch (Exception e) {

        }
    }

    private void processLibraries() {
        boolean adfPresent = false;
        try {
            ObjectName[] libraries = (ObjectName[]) remote.getAttribute(domainMBean, "Libraries");
            for (ObjectName library : libraries) {

                if (library.getKeyProperty("Name").contains("adf.oracle.domain")) {
                    adfPresent = true;
                }
            }
            if (adfPresent) {
                addXMLLibraries(0);
            }
        } catch (Exception e) {
        }
    }

    private void processRealm() {
        try {
            ObjectName securityConfiguration = (ObjectName) remote.getAttribute(domainMBean, "SecurityConfiguration");
            ObjectName defaultRealm = (ObjectName) remote.getAttribute(securityConfiguration, "DefaultRealm");
            ObjectName[] authProviders = (ObjectName[]) remote.getAttribute(defaultRealm, "AuthenticationProviders");
            for (ObjectName authProvider : authProviders) {
                try {
                    elMap.clear();
                    elMap.put("NAME", remote.getAttribute(authProvider, "Name").toString());
                    elMap.put("HOST", remote.getAttribute(authProvider, "Host").toString());
                    elMap.put("PORT", remote.getAttribute(authProvider, "Port").toString());
                    elMap.put("PRINCIPAL", remote.getAttribute(authProvider, "Principal").toString());
                    elMap.put("USERBASEDN", remote.getAttribute(authProvider, "UserBaseDN").toString());
                    elMap.put("GROUPBASEDN", remote.getAttribute(authProvider, "GroupBaseDN").toString());
                    elMap.put("ALLGROUPSFILTER", remote.getAttribute(authProvider, "AllGroupsFilter").toString());
                    elMap.put("ALLUSERSFILTER", remote.getAttribute(authProvider, "AllUsersFilter").toString());
                    addXMLRealm(elMap);
                    elMap.clear();
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {

        }
    }

    private void processServer(ObjectName mbean) {
        try {
            String msName = mbean.getKeyProperty("Name");
            String listenport, machine, state;
            ObjectName serverState = new ObjectName("com.bea:Name=" + msName + ",Type=ServerLifeCycleRuntime");
            listenport = remote.getAttribute(mbean, "ListenPort").toString();
            machine = remote.getAttribute((ObjectName) remote.getAttribute(mbean, "Machine"), "Name").toString();
            state = remote.getAttribute(serverState, "State").toString();
            md5hash = new String(md.digest((msName + " " + listenport + " " + machine + " " + state).getBytes()), "UTF-8");
            if (!msrv.containsKey(md5hash)) {
                elMap.clear();
                elMap.put("NAME", msName);
                elMap.put("LISTENPORT", listenport);
                elMap.put("MACHINE", machine);
                elMap.put("STATE", state);
                addXMLServer(elMap);
                msrv.put(md5hash, "");
                elMap.clear();
            }
        } catch (Exception e) {
        }
    }

    private void processCluster(ObjectName mbean) {
        try {
            if (!mbean.getKeyPropertyList().containsKey("Location")) {
                String clusterName = mbean.getKeyProperty("Name");
                String server;
                ObjectName[] serverNames = (ObjectName[]) remote.getAttribute(mbean, "Servers");
                for (ObjectName serverName : serverNames) {
                    server = serverName.getKeyProperty("Name");
                    md5hash = new String(md.digest((clusterName + " " + server).getBytes()), "UTF-8");
                    if (!mclst.containsKey(md5hash)) {
                        elMap.clear();
                        elMap.put("NAME", clusterName);
                        elMap.put("SERVER", server);
                        addXMLCluster(elMap);
                        mclst.put(md5hash, "");
                        elMap.clear();
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void processNodeManager(ObjectName mbean) {
        try {
            if (!mbean.getKeyPropertyList().containsKey("Location")) {
                String nmname = mbean.getKeyProperty("Name");
                String nmport = remote.getAttribute(mbean, "ListenPort").toString();
                String nmaddr = remote.getAttribute(mbean, "ListenAddress").toString();
                md5hash = new String(md.digest((nmname + " " + nmport + " " + nmaddr).getBytes()), "UTF-8");
                if (!mnm.containsKey(md5hash)) {
                    elMap.clear();
                    elMap.put("NAME", nmname);
                    elMap.put("LISTENADDRESS", nmaddr);
                    elMap.put("LISTENPORT", nmport);
                    addXMLNodeManager(elMap);
                    mnm.put(md5hash, "");
                    elMap.clear();
                }
            }
        } catch (Exception e) {
        }
    }

    private void processJDBCSystemResource(ObjectName mbean) {
        try {
            String url = (String) remote.getAttribute(
                    (ObjectName) remote.getAttribute(
                            (ObjectName) remote.getAttribute(mbean, "JDBCResource"),
                            "JDBCDriverParams"),
                    "Url");
            String[] jndiNames = (String[]) remote.getAttribute(
                    (ObjectName) remote.getAttribute(
                            (ObjectName) remote.getAttribute(mbean, "JDBCResource"),
                            "JDBCDataSourceParams"),
                    "JNDINames");
            String jndi = jndiNames[0];
            ObjectName[] propNames = (ObjectName[]) remote.getAttribute(
                    (ObjectName) remote.getAttribute(
                            (ObjectName) remote.getAttribute(
                                    (ObjectName) remote.getAttribute(mbean, "JDBCResource"),
                                    "JDBCDriverParams"),
                            "Properties"),
                    "Properties");
            String username = "";
            for (ObjectName prop : propNames) {
                info = remote.getMBeanInfo(prop);
                attributes = info.getAttributes();
                for (MBeanAttributeInfo attr : attributes) {
                    try {
                        if ("Name".equals(attr.getName()) && "user".equals(remote.getAttribute(prop, attr.getName()))) {
                            username = (String) remote.getAttribute(prop, "Value");
                        }
                    } catch (Exception e) {
                    }
                }
            }
            String dsName = mbean.getKeyProperty("Name");
            ObjectName[] targetNames = (ObjectName[]) remote.getAttribute(mbean, "Targets");
            for (ObjectName targetName : targetNames) {
                String serverName = targetName.getKeyProperty("Name");
                md5hash = new String(md.digest((dsName + " " + jndi + " " + url + " " + username + " " + serverName).getBytes()), "UTF-8");
                if (!mds.containsKey(md5hash)) {
                    elMap.clear();
                    elMap.put("USERNAME", username);
                    elMap.put("DSNAME", dsName);
                    elMap.put("JNDI", jndi);
                    elMap.put("URL", url);
                    elMap.put("SERVERNAME", serverName);
                    addXMLDatasource(elMap);
                    mds.put(md5hash, "");
                    elMap.clear();
                }
            }
        } catch (Exception e) {
        }
    }

    private void processAppDeployment(ObjectName mbean) {
        try {
            String contextRoot = "";
            ObjectName appRunTime = new ObjectName("");
            ObjectName[] componentRuntimes;
            String appName = mbean.getKeyProperty("Name");
            String location = mbean.getKeyProperty("Location");
            appRunTime
                    = new ObjectName(
                            "com.bea:ServerRuntime="
                            + location
                            + ",Name="
                            + appName.replace("#", "_")
                            + ",Location="
                            + location
                            + ",Type=ApplicationRuntime"
                    );
            componentRuntimes = (ObjectName[]) remote.getAttribute(appRunTime, "ComponentRuntimes");
            for (ObjectName componentRuntime : componentRuntimes) {
                try {
                    contextRoot = (String) remote.getAttribute(componentRuntime, "ContextRoot");
                } catch (Exception e) {
                }
            }
            if (!contextRoot.isEmpty()) {
                md5hash = new String(md.digest((appName + " " + location + " " + contextRoot).getBytes()), "UTF-8");
                if (!mapps.containsKey(md5hash)) {
                    elMap.clear();
                    elMap.put("NAME", appName);
                    elMap.put("SERVER", location);
                    elMap.put("STATE", "N/A");
                    elMap.put("CONTEXTROOT", contextRoot.substring(1));
                    addXMLApp(elMap);
                    mapps.put(md5hash, "");
                    elMap.clear();
                }
            }
        } catch (Exception e) {
        }
    }

    private boolean initOutXML() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            outDoc = dBuilder.newDocument();
            rootOutElement = outDoc.createElement("WebWLInfo");
            outDoc.appendChild(rootOutElement);
            md = MessageDigest.getInstance("MD5");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void addXMLWlHome(String path) {
        Element xmlWLHome = outDoc.createElement("WLHOME");
        xmlWLHome.appendChild(outDoc.createTextNode(path));
        rootOutElement.appendChild(xmlWLHome);
    }

    private void addXMLSuccess() {
        Element xmlSuccess = outDoc.createElement("SUCCESS");
        xmlSuccess.appendChild(outDoc.createTextNode("TRUE"));
        rootOutElement.appendChild(xmlSuccess);
    }

    private void addXMLWlDomainVersion(String version) {
        Element xmlWLVersion = outDoc.createElement("DOMAINVERSION");
        xmlWLVersion.appendChild(outDoc.createTextNode(version));
        rootOutElement.appendChild(xmlWLVersion);
    }

    private void addXMLRealm(Map<String, String> input) {
        Element xmlWLRealm = outDoc.createElement("REALM");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlWLRealm.appendChild(ele);
        }
        rootOutElement.appendChild(xmlWLRealm);
    }

    private void addXMLDomain(Map<String, String> input) {
        Element xmlDomain = outDoc.createElement("DOMAIN");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlDomain.appendChild(ele);
        }
        rootOutElement.appendChild(xmlDomain);
    }

    private void addXMLNodeManager(Map<String, String> input) {
        Element xmlNM = outDoc.createElement("NM");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlNM.appendChild(ele);
        }
        rootOutElement.appendChild(xmlNM);
    }

    private void addXMLCluster(Map<String, String> input) {
        Element xmlCluster = outDoc.createElement("CLUSTER");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlCluster.appendChild(ele);
        }
        rootOutElement.appendChild(xmlCluster);
    }

    private void addXMLServer(Map<String, String> input) {
        Element xmlServer = outDoc.createElement("SERVER");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlServer.appendChild(ele);
        }
        rootOutElement.appendChild(xmlServer);
    }

    private void addXMLDatasource(Map<String, String> input) {
        Element xmlDS = outDoc.createElement("DATASOURCE");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlDS.appendChild(ele);
        }
        rootOutElement.appendChild(xmlDS);
    }

    private void addXMLApp(Map<String, String> input) {
        Element xmlApp = outDoc.createElement("APPLICATION");
        for (String key : input.keySet()) {
            Element ele = outDoc.createElement(key);
            ele.appendChild(outDoc.createTextNode(input.get(key)));
            xmlApp.appendChild(ele);
        }
        rootOutElement.appendChild(xmlApp);
    }

    private void addXMLLibraries(int input) {
        switch (input) {
            case 0:
                Element xmlADF = outDoc.createElement("ADF");
                xmlADF.appendChild(outDoc.createTextNode("TRUE"));
                rootOutElement.appendChild(xmlADF);
                break;
            default:
                break;
        }
    }

    private void addXMLJavaVersion(String version) {
        Element xmlJavaVersion = outDoc.createElement("JAVAVERSION");
        xmlJavaVersion.appendChild(outDoc.createTextNode(version));
        rootOutElement.appendChild(xmlJavaVersion);
    }

    private boolean initT3Connect() {
        try {
            env.put(SECURITY_PRINCIPAL, username);
            env.put(SECURITY_CREDENTIALS, password);
            env.put(PROTOCOL_PROVIDER_PACKAGES, "weblogic.management.remote");
            connector = connect(serviceURL, env);
            remote = connector.getMBeanServerConnection();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        if (!initT3Connect() || !initOutXML()) {
            return ;
        }

        try{
            domainMBean = (ObjectName) remote.getAttribute(service, "DomainConfiguration");
        }catch(Exception e){}

        processDomain();
        processWLVersion();
        processWLHome();
        processJavaVersion();
        processRealm();
        processLibraries();

        try {
            mbeans.addAll(remote.queryNames(null, null));
        } catch (Exception e) {
        }

        for (ObjectName mbean : mbeans) {
            if (mbean.getKeyProperty("Type") != null) {
                switch (mbean.getKeyProperty("Type")) {
                    case "Server":
                        processServer(mbean);
                        break;

                    case "Cluster":
                        processCluster(mbean);
                        break;

                    case "NodeManager":
                        processNodeManager(mbean);
                        break;

                    case "JDBCSystemResource":
                        processJDBCSystemResource(mbean);
                        break;
                    case "AppDeployment":
                        processAppDeployment(mbean);
                        break;
                    default:
                }
            }
        }
        cleanup();
        addXMLSuccess();
        try{
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(outDoc);
            transformer.transform(source, result);
            String xmlString = result.getWriter().toString();
            threadOut.println(xmlString);  
        }catch(Exception e){
            
        }
    }

    private void cleanup() {
        mbeans.clear();
        mds.clear();
        mapps.clear();
        msrv.clear();
        mclst.clear();
        mnm.clear();
        try {
            connector.close();
        } catch (Exception e) {

        }
    }
}
