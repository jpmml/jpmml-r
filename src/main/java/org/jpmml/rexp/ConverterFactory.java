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

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConverterFactory {

	protected ConverterFactory(){
	}

	public <R extends RExp> Converter<R> newConverter(R rexp){
		RStringVector names = (RStringVector)rexp.getAttributeValue("class");

		for(String name : names){
			Class<? extends Converter<?>> clazz = ConverterFactory.converters.get(name);

			if(clazz != null){
				return newConverter(clazz, rexp);
			}
		}

		throw new IllegalArgumentException("No built-in converter for class " + names.getValues());
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

	private static Map<String, Class<? extends Converter<?>>> converters = new LinkedHashMap<>();

	static {
		converters.put("ada", AdaConverter.class);
		converters.put("BinaryTree", BinaryTreeConverter.class);
		converters.put("earth", EarthConverter.class);
		converters.put("elmNN", ElmNNConverter.class);
		converters.put("gbm", GBMConverter.class);
		converters.put("iForest", IForestConverter.class);
		converters.put("glm", GLMConverter.class);
		converters.put("kmeans", KMeansConverter.class);
		converters.put("lm", LMConverter.class);
		converters.put("lrm", LRMConverter.class);
		converters.put("mvr", MVRConverter.class);
		converters.put("ols", OLSConverter.class);
		converters.put("rpart", RPartConverter.class);
		converters.put("randomForest", RandomForestConverter.class);
		converters.put("ranger", RangerConverter.class);
		converters.put("scorecard", ScorecardConverter.class);
		converters.put("svm", SVMConverter.class);
		converters.put("train", TrainConverter.class);
		converters.put("xgb.Booster", XGBoostConverter.class);
	}
}