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

import java.util.LinkedHashMap;
import java.util.Map;

public class ConverterFactory {

	protected ConverterFactory(){
	}

	public Converter newConverter(RExp rexp){
		RStringVector names = (RStringVector)rexp.getAttributeValue("class");

		for(int i = 0; i < names.size(); i++){
			String name = names.getValue(i);

			Class<? extends Converter> clazz = ConverterFactory.converters.get(name);
			if(clazz != null){

				try {
					return clazz.newInstance();
				} catch(Exception e){
					throw new IllegalArgumentException(e);
				}
			}
		}

		throw new IllegalArgumentException();
	}

	static
	public ConverterFactory newInstance(){
		return new ConverterFactory();
	}

	private static Map<String, Class<? extends Converter>> converters = new LinkedHashMap<>();

	static {
		converters.put("BinaryTree", BinaryTreeConverter.class);
		converters.put("gbm", GBMConverter.class);
		converters.put("kmeans", KMeansConverter.class);
		converters.put("iForest", IsolationForestConverter.class);
		converters.put("randomForest", RandomForestConverter.class);
		converters.put("train", TrainConverter.class);
	}
}