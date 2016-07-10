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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedLong;
import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.Batch;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RandomForestConverterTest extends ConverterTest {

	@Test
	public void selectValues(){
		List<String> values = Arrays.asList("1", "2", "3", "4");

		assertEquals(Arrays.asList("1", "3", "4"), RandomForestConverter.selectValues(values, 13d, true));
		assertEquals(Arrays.asList("2"), RandomForestConverter.selectValues(values, 13d, false));
	}

	@Test
	public void toUnsignedLong(){
		assertEquals(UnsignedLong.valueOf("13"), RandomForestConverter.toUnsignedLong(13d));

		assertEquals(UnsignedLong.valueOf("18446744071562067968"), RandomForestConverter.toUnsignedLong(-2147483648d));
	}

	@Test
	public void evaluateFormulaAudit() throws Exception {
		evaluate("RandomForestFormula", "Audit");
	}

	@Test
	public void evaluateAudit() throws Exception {
		evaluate("RandomForest", "Audit");
	}

	@Test
	public void evaluateCaretFormulaAuditMatrix() throws Exception {
		evaluateCaretAudit("TrainRandomForestFormula", "AuditMatrix");
	}

	@Test
	public void evaluateCaretAudit() throws Exception {
		evaluateCaretAudit("TrainRandomForest", "Audit");
	}

	private void evaluateCaretAudit(String name, String dataset) throws Exception {

		try(Batch batch = createBatch(name, dataset)){
			Set<FieldName> ignoredFields = ImmutableSet.of(FieldName.create("probability_0"), FieldName.create("probability_1"));

			evaluate(batch, ignoredFields);
		}
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("RandomForestFormula", "Auto");
	}

	@Test
	public void evaluateAuto() throws Exception {
		evaluate("RandomForest", "Auto");
	}

	@Test
	public void evaluateCaretFormulaAuto() throws Exception {
		evaluate("TrainRandomForestFormula", "Auto");
	}

	@Test
	public void evaluateCaretAuto() throws Exception {
		evaluate("TrainRandomForest", "Auto");
	}

	@Test
	public void evaluateFormulaIris() throws Exception {
		evaluate("RandomForestFormula", "Iris");
	}

	@Test
	public void evaluateCustFormulaIris() throws Exception {
		evaluate("RandomForestCustFormula", "Iris");
	}

	@Test
	public void evaluateIris() throws Exception {
		evaluate("RandomForest", "Iris");
	}

	@Test
	public void evaluateFormulaWineQuality() throws Exception {
		evaluate("RandomForestFormula", "WineQuality");
	}

	@Test
	public void evaluateWineQuality() throws Exception {
		evaluate("RandomForest", "WineQuality");
	}

	@Test
	public void evaluateFormulaWineColor() throws Exception {
		evaluate("RandomForestFormula", "WineColor");
	}

	@Test
	public void evaluateWineColor() throws Exception {
		evaluate("RandomForest", "WineColor");
	}
}