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
package org.jpmml.rexp;

import com.google.common.base.Predicates;
import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.Batch;
import org.junit.Test;

public class GLMConverterTest extends ConverterTest {

	@Test
	public void evaluateFormulaAudit() throws Exception {
		evaluate("GLMFormula", "Audit");
	}

	@Test
	public void evaluateCustFormulaAudit() throws Exception {
		evaluate("GLMCustFormula", "Audit");
	}

	@Test
	public void evaluateCaretFormulaAudit() throws Exception {
		evaluate("TrainGLMFormula", "Audit");
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("GLMFormula", "Auto");

		try(Batch batch = createBatch("GLMFormula", "Auto", Predicates.<FieldName>alwaysTrue(), LMConverter.class)){
			evaluate(batch, null);
		}
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("GLMCustFormula", "Auto");

		try(Batch batch = createBatch("GLMCustFormula", "Auto", Predicates.<FieldName>alwaysTrue(), LMConverter.class)){
			evaluate(batch, null);
		}
	}

	@Test
	public void evaluateCaretFormulaAuto() throws Exception {
		evaluate("TrainGLMFormula", "Auto");
	}

	@Test
	public void evaluateFormulaVisit() throws Exception {
		evaluate("GLMFormula", "Visit");
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("GLMFormula", "WineQuality");
	}

	@Test
	public void evaluateCustFormulaWineQuality() throws Exception {
		evaluate("GLMCustFormula", "WineQuality");
	}
}