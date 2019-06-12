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

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Interval;
import org.dmg.pmml.PMMLFunctions;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.model.ReflectionUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ExpressionTranslatorTest {

	@Test
	public void translate(){
		String string = "(1.0 + log(A / B)) ^ 2";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.POW)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.ADD)
				.addExpressions(PMMLUtil.createConstant("1.0", DataType.DOUBLE))
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.LN)
					.addExpressions(PMMLUtil.createApply(PMMLFunctions.DIVIDE)
						.addExpressions(new FieldRef(FieldName.create("A")), new FieldRef(FieldName.create("B")))
					)
				)
			)
			.addExpressions(PMMLUtil.createConstant("2", DataType.INTEGER));

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateIfExpression(){
		String string = "if(is.na(x)) TRUE else FALSE";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.IF)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.ISMISSING)
				.addExpressions(new FieldRef(FieldName.create("x")))
			)
			.addExpressions(PMMLUtil.createConstant("true", DataType.BOOLEAN), PMMLUtil.createConstant("false", DataType.BOOLEAN));

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateLogicalExpression(){
		String string = "a >= 0.0 & b >= 0.0 | c <= 0.0";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.OR)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.AND)
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.GREATEROREQUAL)
					.addExpressions(new FieldRef(FieldName.create("a")), PMMLUtil.createConstant("0.0", DataType.DOUBLE))
				)
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.GREATEROREQUAL)
					.addExpressions(new FieldRef(FieldName.create("b")), PMMLUtil.createConstant("0.0", DataType.DOUBLE))
				)
			)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.LESSOREQUAL)
				.addExpressions(new FieldRef(FieldName.create("c")), PMMLUtil.createConstant("0.0", DataType.DOUBLE))
			);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateLogicalExpressionChain(){
		String string = "(x == 0) | ((x == 1) | (x == 2)) | x == 3";

		Apply left = PMMLUtil.createApply(PMMLFunctions.EQUAL)
			.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("0", DataType.INTEGER));

		Apply middleLeft = PMMLUtil.createApply(PMMLFunctions.EQUAL)
			.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("1", DataType.INTEGER));

		Apply middleRight = PMMLUtil.createApply(PMMLFunctions.EQUAL)
			.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("2", DataType.INTEGER));

		Apply right = PMMLUtil.createApply(PMMLFunctions.EQUAL)
			.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("3", DataType.INTEGER));

		Expression expected = PMMLUtil.createApply(PMMLFunctions.OR)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.OR)
				.addExpressions(left)
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.OR)
					.addExpressions(middleLeft, middleRight)
				)
			)
			.addExpressions(right);

		Expression actual = ExpressionTranslator.translateExpression(string, false);

		assertTrue(ReflectionUtil.equals(expected, actual));

		expected = PMMLUtil.createApply(PMMLFunctions.OR)
			.addExpressions(left, middleLeft, middleRight, right);

		actual = ExpressionTranslator.translateExpression(string, true);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateRelationalExpression(){
		String string = "if(x < 0) \"negative\" else if(x > 0) \"positive\" else \"zero\"";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.IF)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.LESSTHAN)
				.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("0", DataType.INTEGER))
			)
			.addExpressions(PMMLUtil.createConstant("negative", DataType.STRING))
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.IF)
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.GREATERTHAN)
					.addExpressions(new FieldRef(FieldName.create("x")), PMMLUtil.createConstant("0", DataType.INTEGER))
				)
				.addExpressions(PMMLUtil.createConstant("positive", DataType.STRING))
				.addExpressions(PMMLUtil.createConstant("zero", DataType.STRING))
			);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateArithmeticExpressionChain(){
		String string = "A + B - X + C";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.ADD)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.SUBTRACT)
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.ADD)
					.addExpressions(new FieldRef(FieldName.create("A")), new FieldRef(FieldName.create("B")))
				)
				.addExpressions(new FieldRef(FieldName.create("X")))
			)
			.addExpressions(new FieldRef(FieldName.create("C")));

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateExponentiationExpression(){
		String string = "-2^-3";

		Expression expected = PMMLUtil.createApply(PMMLFunctions.MULTIPLY)
			.addExpressions(PMMLUtil.createConstant(-1))
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.POW)
				.addExpressions(PMMLUtil.createConstant("2", DataType.INTEGER), PMMLUtil.createConstant("-3", DataType.INTEGER))
			);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));

		string = "-2^-2*1.5";

		expected = PMMLUtil.createApply(PMMLFunctions.MULTIPLY)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.MULTIPLY)
				.addExpressions(PMMLUtil.createConstant(-1))
				.addExpressions(PMMLUtil.createApply(PMMLFunctions.POW)
					.addExpressions(PMMLUtil.createConstant("2", DataType.INTEGER), PMMLUtil.createConstant("-2", DataType.INTEGER))
				)
			)
			.addExpressions(PMMLUtil.createConstant("1.5", DataType.DOUBLE));

		actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateFunctionExpression(){
		String string = "parent(first = child(A, log(A)), child(1 + B, right = 0), \"third\" = child(left = 0, c(A, B, C)))";

		FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(string);

		checkFunctionExpression(functionExpression, "parent", "first", null, "third");

		FunctionExpression.Argument first = functionExpression.getArgument("first");
		FunctionExpression.Argument second;

		try {
			second = functionExpression.getArgument("second");

			fail();
		} catch(IllegalArgumentException iae){
			second = functionExpression.getArgument(1);
		}

		FunctionExpression.Argument third = functionExpression.getArgument("third");

		assertEquals("first = child(A, log(A))", first.format());
		assertEquals("child(A, log(A))", first.formatExpression());

		List<Expression> expressions = checkFunctionExpression((FunctionExpression)first.getExpression(), "child", null, null);

		assertTrue(ReflectionUtil.equals(new FieldRef(FieldName.create("A")), expressions.get(0)));
		assertTrue(ReflectionUtil.equals(PMMLUtil.createApply(PMMLFunctions.LN, new FieldRef(FieldName.create("A"))), expressions.get(1)));

		assertEquals("child(1 + B, right = 0)", second.format());
		assertEquals("child(1 + B, right = 0)", second.formatExpression());

		expressions = checkFunctionExpression((FunctionExpression)second.getExpression(), "child", null, "right");

		assertTrue(ReflectionUtil.equals(PMMLUtil.createApply(PMMLFunctions.ADD, PMMLUtil.createConstant("1", DataType.INTEGER), new FieldRef(FieldName.create("B"))), expressions.get(0)));
		assertTrue(ReflectionUtil.equals(PMMLUtil.createConstant("0", DataType.INTEGER), expressions.get(1)));

		assertEquals("\"third\" = child(left = 0, c(A, B, C))", third.format());
		assertEquals("child(left = 0, c(A, B, C))", third.formatExpression());

		expressions = checkFunctionExpression((FunctionExpression)third.getExpression(), "child", "left", null);

		assertTrue(ReflectionUtil.equals(PMMLUtil.createConstant("0", DataType.INTEGER), expressions.get(0)));

		checkFunctionExpression((FunctionExpression)expressions.get(1), "c", null, null, null);
	}

	@Test
	public void translateParenthesizedExpression(){
		String string = "TRUE | TRUE & FALSE";

		Constant trueConstant = PMMLUtil.createConstant("true", DataType.BOOLEAN);
		Constant falseConstant = PMMLUtil.createConstant("false", DataType.BOOLEAN);

		Expression expected = PMMLUtil.createApply(PMMLFunctions.OR)
			.addExpressions(trueConstant)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.AND)
				.addExpressions(trueConstant, falseConstant)
			);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));

		string = "(TRUE | TRUE) & FALSE";

		expected = PMMLUtil.createApply(PMMLFunctions.AND)
			.addExpressions(PMMLUtil.createApply(PMMLFunctions.OR)
				.addExpressions(trueConstant, trueConstant)
			)
			.addExpressions(falseConstant);

		actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateInterval(){
		Interval expected = new Interval(Interval.Closure.OPEN_CLOSED)
			.setLeftMargin(new Double("-10.0E0"))
			.setRightMargin(new Double("+10.0E0"));

		Interval actual = ExpressionTranslator.translateInterval("(-10.0E+0, +10.0E-0]");

		assertTrue(ReflectionUtil.equals(expected, actual));

		try {
			ExpressionTranslator.translateInterval("(0, NaN)");

			fail();
		} catch(IllegalArgumentException iae){
			// Ignored
		}

		expected = new Interval(Interval.Closure.CLOSED_CLOSED)
			.setLeftMargin(null)
			.setRightMargin(null);

		actual = ExpressionTranslator.translateInterval("[-Inf, +Inf]");

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	static
	private List<Expression> checkFunctionExpression(FunctionExpression functionExpression, String function, String... tags){
		assertEquals(function, functionExpression.getFunction());

		List<FunctionExpression.Argument> arguments = functionExpression.getArguments();
		assertEquals(tags.length, arguments.size());

		List<Expression> expressions = new ArrayList<>();

		for(int i = 0; i < arguments.size(); i++){
			FunctionExpression.Argument argument = arguments.get(i);

			String tag = argument.getTag();
			Expression expression = argument.getExpression();

			assertEquals(tag, tags[i]);

			expressions.add(expression);
		}

		return expressions;
	}
}