/* **************************************************************************** 
 * CIShell: Cyberinfrastructure Shell, An Algorithm Integration Framework.
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Apache License v2.0 which accompanies
 * this distribution, and is available at:
 * http://www.apache.org/licenses/LICENSE-2.0.html
 * 
 * Created on Jul 31, 2006 at Indiana University.
 * 
 * Contributors:
 *     Indiana University - 
 * ***************************************************************************/
package org.cishell.templates.staticexecutable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.cishell.framework.CIShellContext;
import org.cishell.framework.algorithm.Algorithm;
import org.cishell.framework.algorithm.AlgorithmFactory;
import org.cishell.framework.data.Data;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.log.LogService;
import org.osgi.service.metatype.MetaTypeProvider;
import org.osgi.service.metatype.MetaTypeService;


public class StaticExecutableAlgorithmFactory implements AlgorithmFactory {
    BundleContext bContext;
    String algName;
    MetaTypeProvider provider;
    
    public StaticExecutableAlgorithmFactory() {}
    
    public StaticExecutableAlgorithmFactory(String algName, BundleContext bContext) {
    	this.algName = algName;
    	this.bContext = bContext;
    }

    protected void activate(ComponentContext ctxt) {
        bContext = ctxt.getBundleContext();
        algName = (String)ctxt.getProperties().get("Algorithm-Directory");
        
        try {
            MetaTypeService mts = (MetaTypeService) ctxt.locateService("MTS");
            provider = mts.getMetaTypeInformation(ctxt.getBundleContext().getBundle());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    protected void deactivate(ComponentContext ctxt) {
        bContext = null;
        algName = null;
    }

    public Algorithm createAlgorithm(Data[] data, Dictionary parameters, CIShellContext context) {
        return new StaticExecutableAlgorithm(data, parameters, context);
    }

    public MetaTypeProvider createParameters(Data[] data) {
        return provider;
    }
    
    private class StaticExecutableAlgorithm implements Algorithm {
        private String ALGORITHM;
		private String ALGORITHM_MACOSX_PPC;
		private String MACOSX;
		private String ALGORITHM_WIN32;
		private String WIN32;
		private String ALGORITHM_LINUX_X86;
		private String LINUX;
		private String ALGORITHM_DEFAULT;
		Data[] data;
        Dictionary parameters;
        CIShellContext context;
        LogService logger;
        
        public StaticExecutableAlgorithm(Data[] data, Dictionary parameters, CIShellContext context) {
            this.data = data;
            this.parameters = parameters;
            this.context = context;
            logger = (LogService)context.getService(LogService.class.getName());
            
            ALGORITHM = algName + "/";
            ALGORITHM_MACOSX_PPC = ALGORITHM + "macosx.ppc/";
    		MACOSX = "macosx";
    		ALGORITHM_WIN32 = ALGORITHM + "win32/";
    		WIN32 = "win32";
    		ALGORITHM_LINUX_X86 = ALGORITHM + "linux.x86/";
    		LINUX = "linux";
    		ALGORITHM_DEFAULT = ALGORITHM + "default/";
        }

        public Data[] execute() {
            try {
                Properties serviceProps = getProperties("/"+algName+"/service.properties");
                Properties configProps = getProperties("/"+algName+"/config.properties");
                
                serviceProps.putAll(configProps);
                serviceProps.put("Algorithm-Directory", algName);
                
                StaticExecutableRunner runner = 
                    new StaticExecutableRunner(bContext, context, serviceProps, parameters, data);
                
                copyFiles(runner.getTempDirectory());
            
                return runner.execute();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        
        private void copyFiles(File dir) throws IOException {
            Enumeration e = bContext.getBundle().getEntryPaths("/"+algName);
            
            Set entries = new HashSet();
            
			while(e != null && e.hasMoreElements()) {
				String entryPath = (String) e.nextElement();
				//logger.log(LogService.LOG_DEBUG, "entry: " + entryPath + "\n\n");
				if(entryPath.endsWith("/")) {
					entries.add(entryPath);
				}
			}
            
            dir = new File(dir.getPath() + File.separator + algName);
            dir.mkdirs();
            
            String os = bContext.getProperty("osgi.os");
            String arch = bContext.getProperty("osgi.arch");
            
            String path = null;
            
            //take the default, if there
            if(entries.contains(ALGORITHM_DEFAULT)) {
            	String default_path = ALGORITHM_DEFAULT;
            	//logger.log(LogService.LOG_DEBUG, "base path: "+default_path+
            	//		"\n\t"+dir.getAbsolutePath() + "\n\n");
            	copyDir(dir, default_path, 0);
            }
            
            //but override with platform idiosyncracies
            if(os.equals(WIN32) && entries.contains(ALGORITHM_WIN32)) {
            	path = ALGORITHM_WIN32;
            } else if(os.equals(MACOSX) && entries.contains(ALGORITHM_MACOSX_PPC)) {
            	path = ALGORITHM_MACOSX_PPC;
            } else if(os.equals(LINUX) && entries.contains(ALGORITHM_LINUX_X86)) {
            	path = ALGORITHM_LINUX_X86;
            }
            
            String platform_path = ALGORITHM + os + "." + arch + "/";
			//and always override anything with an exact match
            if(entries.contains(platform_path)) {
            	path = platform_path;
            }
            
            if (path == null) {
                throw new RuntimeException("Unable to find compatible executable");
            } else {
            	//logger.log(LogService.LOG_DEBUG, "base path: "+path+
            	//		"\n\t"+dir.getAbsolutePath() + "\n\n");
            	copyDir(dir, path, 0);
            }
        }
        
        private void copyDir(File dir, String dirPath, int depth) throws IOException {
            Enumeration e = bContext.getBundle().getEntryPaths(dirPath);
            
            //dirPath = dirPath.replace('/', File.separatorChar);
            
            while (e != null && e.hasMoreElements()) {
                String path = (String)e.nextElement();
                
                if (path.endsWith("/")) {
                    String dirName = getName(path);
                    
                    File subDirectory = new File(dir.getPath() + File.separator + dirName);
                    subDirectory.mkdirs();
                    //logger.log(LogService.LOG_DEBUG, "path: " + depth + " "+path+
                	//		"\n\t"+subDirectory.getAbsolutePath() + "\n\n");
                    copyDir(subDirectory, path, depth + 1);
                } else {
                    copyFile(dir, path);
                }
            }
        }
        
        private void copyFile(File dir, String path) throws IOException {
            URL entry = bContext.getBundle().getEntry(path);
            
            //path = path.replace('/', File.separatorChar);
            String file = getName(path);
            FileOutputStream outStream = new FileOutputStream(dir.getPath() + File.separator + file);
            
            ReadableByteChannel in = Channels.newChannel(entry.openStream());
            FileChannel out = outStream.getChannel();
            out.transferFrom(in, 0, Integer.MAX_VALUE);
            
            in.close();
            out.close();
        }
        
        private String getName(String path) {
            if (path.lastIndexOf('/') == path.length()-1) {
                path = path.substring(0, path.length()-1);
            }
            
            path = path.substring(path.lastIndexOf('/')+1, 
                    path.length());
            
            return path;
        }
        
        private Properties getProperties(String entry) throws IOException {
            URL url = bContext.getBundle().getEntry(entry);
            Properties props = null;
            
            if (url != null) {
                props = new Properties();
                props.load(url.openStream());
            }
            return props;
        }
    }
}
