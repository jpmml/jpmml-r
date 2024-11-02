/*
 * Copyright (c) 2024 Villu Ruusmann
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
package org.jpmml.rexp.evaluator;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import org.jpmml.evaluator.Function;
import org.jpmml.evaluator.FunctionRegistry;
import org.jpmml.rexp.evaluator.functions.PPois;

/**
 * @see FunctionRegistry
 */
public class RExpFunctionRegistry {

	private RExpFunctionRegistry(){
	}

	static
	public void propagate(String name){
		propagate(key -> Objects.equals(name, key));
	}

	static
	public void propagateAll(){
		propagate(key -> true);
	}

	static
	private void propagate(Predicate<String> predicate){
		(RExpFunctionRegistry.rexpFunctions.entrySet()).stream()
			.filter(entry -> predicate.test(entry.getKey()))
			.forEach(entry -> FunctionRegistry.putFunction(entry.getKey(), entry.getValue()));

		(RExpFunctionRegistry.rexpFunctionClazzes.entrySet()).stream()
			.filter(entry -> predicate.test(entry.getKey()))
			.forEach(entry -> FunctionRegistry.putFunction(entry.getKey(), entry.getValue()));
	}

	private static final Map<String, Function> rexpFunctions;

	static {
		ImmutableMap.Builder<String, Function> builder = new ImmutableMap.Builder<>();

		builder.put(RExpFunctions.PPOIS, new PPois());

		rexpFunctions = builder.build();
	}

	private static final Map<String, Class<? extends Function>> rexpFunctionClazzes = Collections.emptyMap();
}