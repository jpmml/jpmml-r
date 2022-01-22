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
import org.jpmml.converter.testing.ModelEncoderBatchTest;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.PMMLEquivalence;

abstract
public class RExpEncoderBatchTest extends ModelEncoderBatchTest {

	public RExpEncoderBatchTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	public RExpEncoderBatch createBatch(String algorithm, String dataset, Class<? extends Converter<? extends RExp>> converterClazz){
		Predicate<ResultField> columnFilter = (resultField -> true);
		Equivalence<Object> equivalence = getEquivalence();

		RExpEncoderBatch batch = createBatch(algorithm, dataset, columnFilter, equivalence);
		batch.setConverterClazz(converterClazz);

		return batch;
	}

	@Override
	public RExpEncoderBatch createBatch(String algorithm, String dataset, Predicate<ResultField> columnFilter, Equivalence<Object> equivalence){
		RExpEncoderBatch result = new RExpEncoderBatch(algorithm, dataset, columnFilter, equivalence){

			@Override
			public RExpEncoderBatchTest getArchiveBatchTest(){
				return RExpEncoderBatchTest.this;
			}
		};

		return result;
	}
}