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

import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.ArchiveBatch;
import org.jpmml.evaluator.testing.IntegrationTest;
import org.jpmml.evaluator.testing.PMMLEquivalence;

abstract
public class RExpTest extends IntegrationTest {

	public RExpTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	protected ArchiveBatch createBatch(String name, String dataset, Class<? extends Converter<? extends RExp>> converterClazz){
		Predicate<ResultField> predicate = (resultField -> true);
		Equivalence<Object> equivalence = getEquivalence();

		RExpTestBatch batch = (RExpTestBatch)createBatch(name, dataset, predicate, equivalence);
		batch.setConverterClazz(converterClazz);

		return batch;
	}

	@Override
	protected ArchiveBatch createBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		ArchiveBatch result = new RExpTestBatch(name, dataset, predicate, equivalence){

			@Override
			public RExpTest getIntegrationTest(){
				return RExpTest.this;
			}
		};

		return result;
	}
}