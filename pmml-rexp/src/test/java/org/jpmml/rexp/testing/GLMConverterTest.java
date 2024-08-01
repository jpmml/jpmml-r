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
package org.jpmml.rexp.testing;

import org.jpmml.converter.testing.Datasets;
import org.jpmml.evaluator.testing.Batch;
import org.jpmml.rexp.LMConverter;
import org.junit.Test;

public class GLMConverterTest extends RExpEncoderBatchTest implements Datasets {

	@Test
	public void evaluateFormulaAudit() throws Exception {
		evaluate("GLMFormula", AUDIT);
	}

	@Test
	public void evaluateWrappedFormulaAudit() throws Exception {
		evaluate("WrappedGLMFormula", AUDIT);
	}

	@Test
	public void evaluateCustFormulaAudit() throws Exception {
		evaluate("GLMCustFormula", AUDIT);
	}

	@Test
	public void evaluateCaretFormulaAudit() throws Exception {
		evaluate("TrainGLMFormula", AUDIT);
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("GLMFormula", AUTO);

		try(Batch batch = createBatch("GLMFormula", AUTO, LMConverter.class)){
			evaluate(batch);
		}
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("GLMCustFormula", AUTO);

		try(Batch batch = createBatch("GLMCustFormula", AUTO, LMConverter.class)){
			evaluate(batch);
		}
	}

	@Test
	public void evaluateCaretFormulaAuto() throws Exception {
		evaluate("TrainGLMFormula", AUTO);
	}

	@Test
	public void evaluateFormulaVisit() throws Exception {
		evaluate("GLMFormula", VISIT);
	}

	@Test
	public void evaluateStatmodGLMFormulaVisit() throws Exception {
		evaluate("StatmodGLMFormula", VISIT);
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("GLMFormula", WINE_QUALITY);
	}

	@Test
	public void evaluateCustFormulaWineQuality() throws Exception {
		evaluate("GLMCustFormula", WINE_QUALITY);
	}
}