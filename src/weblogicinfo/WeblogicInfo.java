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

import java.lang.management.ManagementFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

public class WeblogicInfo {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception{

        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");  
        System.setProperty("weblogic.ThreadPoolSize","100");
        System.setProperty("weblogic.MaxMessageSize", "300000000"); 
        //System.setProperty("weblogic.MaxOpenSockCount", "1024"); 
        System.setProperty("weblogic.ThreadPoolPercentSocketReaders", "100");         
        
        Server server = new Server(80);

        // Setup JMX
        MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        server.addBean(mbContainer);
               
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(WebWLInfo.class, "/*");
        server.start();
        
        server.dumpStdErr();

        server.join();
    }
    
}
