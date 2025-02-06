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
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Interval;
import org.dmg.pmml.PMMLFunctions;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.model.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ExpressionTranslatorTest {

	@Test
	public void translate(){
		String string = "(1.0 + log(A / B)) ^ 2";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.POW,
			ExpressionUtil.createApply(PMMLFunctions.ADD,
				ExpressionUtil.createConstant(DataType.DOUBLE, "1.0"),
				ExpressionUtil.createApply(PMMLFunctions.LN,
					ExpressionUtil.createApply(PMMLFunctions.DIVIDE,
						new FieldRef("A"),
						new FieldRef("B")
					)
				)
			),
			ExpressionUtil.createConstant(DataType.INTEGER, "2")
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateIfExpression(){
		String string = "if(is.na(x)) TRUE else FALSE";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.IF,
			ExpressionUtil.createApply(PMMLFunctions.ISMISSING, new FieldRef("x")),
			ExpressionUtil.createConstant(DataType.BOOLEAN, "true"),
			ExpressionUtil.createConstant(DataType.BOOLEAN, "false")
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateLogicalExpression(){
		String string = "a >= 0.0 & b >= 0.0 | c <= 0.0";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.OR,
			ExpressionUtil.createApply(PMMLFunctions.AND,
				ExpressionUtil.createApply(PMMLFunctions.GREATEROREQUAL, new FieldRef("a"), ExpressionUtil.createConstant(DataType.DOUBLE, "0.0")),
				ExpressionUtil.createApply(PMMLFunctions.GREATEROREQUAL, new FieldRef("b"), ExpressionUtil.createConstant(DataType.DOUBLE, "0.0"))
			),
			ExpressionUtil.createApply(PMMLFunctions.LESSOREQUAL, new FieldRef("c"), ExpressionUtil.createConstant(DataType.DOUBLE, "0.0"))
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateLogicalExpressionChain(){
		String string = "(x == 0) | ((x == 1) | (x == 2)) | x == 3";

		Apply left = ExpressionUtil.createApply(PMMLFunctions.EQUAL, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "0"));
		Apply middleLeft = ExpressionUtil.createApply(PMMLFunctions.EQUAL, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "1"));
		Apply middleRight = ExpressionUtil.createApply(PMMLFunctions.EQUAL, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "2"));
		Apply right = ExpressionUtil.createApply(PMMLFunctions.EQUAL, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "3"));

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.OR,
			ExpressionUtil.createApply(PMMLFunctions.OR,
				left,
				ExpressionUtil.createApply(PMMLFunctions.OR,
					middleLeft, middleRight
				)
			),
			right
		);

		Expression actual = ExpressionTranslator.translateExpression(string, false);

		assertTrue(ReflectionUtil.equals(expected, actual));

		expected = ExpressionUtil.createApply(PMMLFunctions.OR, left, middleLeft, middleRight, right);

		actual = ExpressionTranslator.translateExpression(string, true);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateRelationalExpression(){
		String string = "if(x < 0) \"negative\" else if(x > 0) \"positive\" else \"zero\"";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.IF,
			ExpressionUtil.createApply(PMMLFunctions.LESSTHAN, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "0")),
			ExpressionUtil.createConstant(DataType.STRING, "negative"),
			ExpressionUtil.createApply(PMMLFunctions.IF,
				ExpressionUtil.createApply(PMMLFunctions.GREATERTHAN, new FieldRef("x"), ExpressionUtil.createConstant(DataType.INTEGER, "0")),
				ExpressionUtil.createConstant(DataType.STRING, "positive"),
				ExpressionUtil.createConstant(DataType.STRING, "zero")
			)
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateArithmeticExpressionChain(){
		String string = "A + B - X + C";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.ADD,
			ExpressionUtil.createApply(PMMLFunctions.SUBTRACT,
				ExpressionUtil.createApply(PMMLFunctions.ADD, new FieldRef("A"), new FieldRef("B")),
				new FieldRef("X")
			),
			new FieldRef("C")
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));
	}

	@Test
	public void translateExponentiationExpression(){
		String string = "-2^-3";

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY,
			ExpressionUtil.createConstant(-1),
			ExpressionUtil.createApply(PMMLFunctions.POW, ExpressionUtil.createConstant(DataType.INTEGER, "2"), ExpressionUtil.createConstant(DataType.INTEGER, "-3"))
		);

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));

		string = "-2^-2*1.5";

		expected = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY,
			ExpressionUtil.createApply(PMMLFunctions.MULTIPLY,
				ExpressionUtil.createConstant(-1),
				ExpressionUtil.createApply(PMMLFunctions.POW, ExpressionUtil.createConstant(DataType.INTEGER, "2"), ExpressionUtil.createConstant(DataType.INTEGER, "-2"))
			),
			ExpressionUtil.createConstant(DataType.DOUBLE, "1.5")
		);

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

		assertTrue(ReflectionUtil.equals(new FieldRef("A"), expressions.get(0)));
		assertTrue(ReflectionUtil.equals(ExpressionUtil.createApply(PMMLFunctions.LN, new FieldRef("A")), expressions.get(1)));

		assertEquals("child(1 + B, right = 0)", second.format());
		assertEquals("child(1 + B, right = 0)", second.formatExpression());

		expressions = checkFunctionExpression((FunctionExpression)second.getExpression(), "child", null, "right");

		assertTrue(ReflectionUtil.equals(ExpressionUtil.createApply(PMMLFunctions.ADD, ExpressionUtil.createConstant(DataType.INTEGER, "1"), new FieldRef("B")), expressions.get(0)));
		assertTrue(ReflectionUtil.equals(ExpressionUtil.createConstant(DataType.INTEGER, "0"), expressions.get(1)));

		assertEquals("\"third\" = child(left = 0, c(A, B, C))", third.format());
		assertEquals("child(left = 0, c(A, B, C))", third.formatExpression());

		expressions = checkFunctionExpression((FunctionExpression)third.getExpression(), "child", "left", null);

		assertTrue(ReflectionUtil.equals(ExpressionUtil.createConstant(DataType.INTEGER, "0"), expressions.get(0)));

		checkFunctionExpression((FunctionExpression)expressions.get(1), "c", null, null, null);
	}

	@Test
	public void translateParenthesizedExpression(){
		String string = "TRUE | TRUE & FALSE";

		Constant trueConstant = ExpressionUtil.createConstant(DataType.BOOLEAN, "true");
		Constant falseConstant = ExpressionUtil.createConstant(DataType.BOOLEAN, "false");

		Expression expected = ExpressionUtil.createApply(PMMLFunctions.OR, trueConstant, ExpressionUtil.createApply(PMMLFunctions.AND, trueConstant, falseConstant));

		Expression actual = ExpressionTranslator.translateExpression(string);

		assertTrue(ReflectionUtil.equals(expected, actual));

		string = "(TRUE | TRUE) & FALSE";

		expected = ExpressionUtil.createApply(PMMLFunctions.AND, ExpressionUtil.createApply(PMMLFunctions.OR, trueConstant, trueConstant), falseConstant);

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