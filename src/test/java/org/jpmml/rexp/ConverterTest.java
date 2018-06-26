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

import java.io.InputStream;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.ArchiveBatch;
import org.jpmml.evaluator.IntegrationTest;
import org.jpmml.evaluator.IntegrationTestBatch;
import org.jpmml.evaluator.PMMLEquivalence;

abstract
public class ConverterTest extends IntegrationTest {

	public ConverterTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	public ConverterTest(Equivalence<Object> equivalence){
		super(equivalence);
	}

	@Override
	protected ArchiveBatch createBatch(String name, String dataset, Predicate<FieldName> predicate){
		return createBatch(name, dataset, predicate, null);
	}

	protected ArchiveBatch createBatch(String name, String dataset, Predicate<FieldName> predicate, final Class<? extends Converter<? extends RExp>> clazz){
		ArchiveBatch result = new IntegrationTestBatch(name, dataset, predicate){

			@Override
			public IntegrationTest getIntegrationTest(){
				return ConverterTest.this;
			}

			@Override
			public PMML getPMML() throws Exception {

				try(InputStream is = open("/rds/" + getName() + getDataset() + ".rds")){
					RExpParser parser = new RExpParser(is);

					RExp rexp = parser.parse();

					PMML pmml = convert(rexp, clazz);

					ensureValidity(pmml);

					return pmml;
				}
			}
		};

		return result;
	}

	static
	private PMML convert(RExp rexp, Class<? extends Converter<? extends RExp>> clazz) throws Exception {
		ConverterFactory converterFactory = ConverterFactory.newInstance();

		Converter<RExp> converter;

		if(clazz != null){
			converter = converterFactory.newConverter(clazz, rexp);
		} else

		{
			converter = converterFactory.newConverter(rexp);
		}

		return converter.encodePMML();
	}
}