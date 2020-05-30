/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.IntegrationTestBatch;

abstract
public class ConverterTestBatch extends IntegrationTestBatch {

	private Class<? extends Converter<? extends RExp>> converterClazz = null;


	public ConverterTestBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		super(name, dataset, predicate, equivalence);
	}

	public Converter<RExp> createConverter(RExp rexp){
		Class<? extends Converter<? extends RExp>> converterClazz = getConverterClazz();

		ConverterFactory converterFactory = ConverterFactory.newInstance();

		Converter<RExp> converter;

		if(converterClazz != null){
			converter = converterFactory.newConverter(converterClazz, rexp);
		} else

		{
			converter = converterFactory.newConverter(rexp);
		}

		return converter;
	}

	public Class<? extends Converter<? extends RExp>> getConverterClazz(){
		return this.converterClazz;
	}

	void setConverterClazz(Class<? extends Converter<? extends RExp>> converterClazz){
		this.converterClazz = converterClazz;
	}
}