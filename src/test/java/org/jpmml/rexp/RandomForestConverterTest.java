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
import java.util.function.Predicate;

import com.google.common.primitives.UnsignedLong;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RandomForestConverterTest extends ConverterTest {

	@Test
	public void selectValues(){
		List<String> values = Arrays.asList("1", "2", "3", "4");

		Predicate<Object> truePredicate = new Predicate<Object>(){

			@Override
			public boolean test(Object value){
				return true;
			}
		};

		assertEquals(Arrays.asList("1", "3", "4"), RandomForestConverter.selectValues(values, truePredicate, 13d, true));
		assertEquals(Arrays.asList("2"), RandomForestConverter.selectValues(values, truePredicate, 13d, false));
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
	public void evaluateCustFormulaAudit() throws Exception {
		evaluate("RandomForestCustFormula", "Audit");
	}

	@Test
	public void evaluateAudit() throws Exception {
		evaluate("RandomForest", "Audit");
	}

	@Test
	public void evaluateCaretFormulaAudit() throws Exception {
		evaluate("TrainRandomForestFormula", "Audit");
	}

	@Test
	public void evaluateCaretAudit() throws Exception {
		evaluate("TrainRandomForest", "Audit");
	}

	@Test
	public void evaluateFormulaAuto() throws Exception {
		evaluate("RandomForestFormula", "Auto");
	}

	@Test
	public void evaluateCustFormulaAuto() throws Exception {
		evaluate("RandomForestCustFormula", "Auto");
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
	public void evaluateCaretIris() throws Exception {
		evaluate("TrainRandomForest", "Iris");
	}

	@Test
	public void evaluateCaretFormulaIris() throws Exception {
		evaluate("TrainRandomForestFormula", "Iris");
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