/*
 * Copyright (c) 2015 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterFactory {

	protected ConverterFactory(){
	}

	public <R extends RExp> Converter<R> newConverter(R rexp){
		RStringVector classNames = RExpUtil.getClassNames(rexp);

		for(String className : classNames){
			Class<? extends Converter<?>> clazz = ConverterFactory.converters.get(className);

			if(clazz != null){
				return newConverter(clazz, rexp);
			}
		}

		throw new IllegalArgumentException("No built-in converter for class " + classNames.getValues());
	}

	public <R extends RExp> Converter<R> newConverter(Class<? extends Converter<?>> clazz, R rexp){

		try {
			Constructor<?> constructor = clazz.getDeclaredConstructor(rexp.getClass());

			return (Converter<R>)constructor.newInstance(rexp);
		} catch(Exception e){
			throw new IllegalArgumentException(e);
		}
	}

	static
	public ConverterFactory newInstance(){
		return new ConverterFactory();
	}

	static
	private void init(){
		Thread thread = Thread.currentThread();

		ClassLoader classLoader = thread.getContextClassLoader();
		if(classLoader == null){
			classLoader = ClassLoader.getSystemClassLoader();
		}

		Enumeration<URL> urls;

		try {
			urls = classLoader.getResources("META-INF/r2pmml.properties");
		} catch(IOException ioe){
			logger.warn("Failed to find resources", ioe);

			return;
		}

		while(urls.hasMoreElements()){
			URL url = urls.nextElement();

			logger.trace("Loading resource " + url);

			try(InputStream is = url.openStream()){
				Properties properties = new Properties();
				properties.load(is);

				init(classLoader, properties);
			} catch(IOException ioe){
				logger.warn("Failed to load resource", ioe);
			}
		}
	}

	static
	private void init(ClassLoader classLoader, Properties properties){

		if(properties.isEmpty()){
			return;
		}

		Set<String> keys = properties.stringPropertyNames();
		for(String key : keys){
			String value = properties.getProperty(key);

			logger.trace("Mapping R class " + key + " to converter class " + value);

			Class<? extends Converter<?>> converterClazz;

			try {
				converterClazz = (Class)classLoader.loadClass(value);
			} catch(ClassNotFoundException cnfe){
				logger.warn("Failed to load converter class", cnfe);

				continue;
			}

			if(!(Converter.class).isAssignableFrom(converterClazz)){
				throw new IllegalArgumentException("Converter class " + converterClazz.getName() + " is not a subclass of " + Converter.class.getName());
			}

			ConverterFactory.converters.put(key, converterClazz);
		}
	}

	private static Map<String, Class<? extends Converter<?>>> converters = new LinkedHashMap<>();

	private static final Logger logger = LoggerFactory.getLogger(ConverterFactory.class);

	static {
		ConverterFactory.init();
	}
}