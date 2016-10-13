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

import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExpressionTranslatorTest {

	@Test
	public void translate(){
		Apply apply = (Apply)ExpressionTranslator.translate("(1.0 + log(A / B)) ^ 2");

		List<Expression> expressions = checkApply(apply, "pow", Apply.class, Constant.class);

		Expression left = expressions.get(0);
		Expression right = expressions.get(1);

		checkConstant((Constant)right, "2", null);

		expressions = checkApply((Apply)left, "+", Constant.class, Apply.class);

		left = expressions.get(0);
		right = expressions.get(1);

		checkConstant((Constant)left, "1.0", DataType.DOUBLE);

		expressions = checkApply((Apply)right, "ln", Apply.class);

		left = expressions.get(0);

		{
			expressions = checkApply((Apply)left, "/", FieldRef.class, FieldRef.class);

			left = expressions.get(0);
			right = expressions.get(1);

			checkFieldRef((FieldRef)left, FieldName.create("A"));
			checkFieldRef((FieldRef)right, FieldName.create("B"));
		}
	}

	@Test
	public void translateIfExpression(){
		Apply apply = (Apply)ExpressionTranslator.translate("if(TRUE) 1 else 0");

		List<Expression> expressions = checkApply(apply, "if", Constant.class, Constant.class, Constant.class);

		Expression condition = expressions.get(0);

		checkConstant((Constant)condition, "true", DataType.BOOLEAN);

		Expression first = expressions.get(1);
		Expression second = expressions.get(2);

		checkConstant((Constant)first, "1", null);
		checkConstant((Constant)second, "0", null);
	}

	static
	private List<Expression> checkApply(Apply apply, String function, Class<? extends Expression>... expressionClazzes){
		assertEquals(function, apply.getFunction());

		List<Expression> expressions = apply.getExpressions();
		assertEquals(expressionClazzes.length, expressions.size());

		for(int i = 0; i < expressionClazzes.length; i++){
			Class<? extends Expression> expressionClazz = expressionClazzes[i];
			Expression expression = expressions.get(i);

			assertTrue(expressionClazz.isInstance(expression));
		}

		return expressions;
	}

	static
	private void checkFieldRef(FieldRef fieldRef, FieldName name){
		assertEquals(name, fieldRef.getField());
	}

	static
	private void checkConstant(Constant constant, String value, DataType dataType){
		assertEquals(value, constant.getValue());
		assertEquals(dataType, constant.getDataType());
	}
}