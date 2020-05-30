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
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.ArchiveBatch;
import org.jpmml.evaluator.testing.IntegrationTest;
import org.jpmml.evaluator.testing.PMMLEquivalence;

abstract
public class ConverterTest extends IntegrationTest {

	public ConverterTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	protected ArchiveBatch createBatch(String name, String dataset, Class<? extends Converter<? extends RExp>> converterClazz){
		Predicate<ResultField> predicate = (resultField -> true);
		Equivalence<Object> equivalence = getEquivalence();

		ConverterTestBatch batch = (ConverterTestBatch)createBatch(name, dataset, predicate, equivalence);
		batch.setConverterClazz(converterClazz);

		return batch;
	}

	@Override
	protected ArchiveBatch createBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		ArchiveBatch result = new ConverterTestBatch(name, dataset, predicate, equivalence){

			@Override
			public IntegrationTest getIntegrationTest(){
				return ConverterTest.this;
			}

			@Override
			public PMML getPMML() throws Exception {

				try(InputStream is = open("/rds/" + getName() + getDataset() + ".rds")){
					RExpParser parser = new RExpParser(is);

					RExp rexp = parser.parse();

					Converter<RExp> converter = createConverter(rexp);

					PMML pmml = converter.encodePMML();

					validatePMML(pmml);

					return pmml;
				}
			}
		};

		return result;
	}
}