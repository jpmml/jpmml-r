/*
 * Copyright (c) 2016 Villu Ruusmann
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
package org.jpmml.rexp.xgboost.testing;

import org.jpmml.converter.testing.Datasets;
import org.jpmml.converter.testing.Fields;
import org.jpmml.evaluator.testing.FloatEquivalence;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.jpmml.rexp.testing.RExpEncoderBatchTest;
import org.junit.jupiter.api.Test;

public class XGBoostConverterTest extends RExpEncoderBatchTest implements Datasets, Fields {

	public XGBoostConverterTest(){
		super();
	}

	@Test
	public void evaluateAuto() throws Exception {
		evaluate("XGBoost", AUTO, new FloatEquivalence(8 + 4));
	}

	@Test
	public void evaluateAuditNA() throws Exception {
		evaluate("XGBoost", AUDIT_NA, excludeFields(AUDIT_PROBABILITY_FALSE), new PMMLEquivalence(5e-5, 5e-5));
	}

	@Test
	public void evaluateIris() throws Exception {
		evaluate("XGBoost", IRIS, new FloatEquivalence(16));
	}
}