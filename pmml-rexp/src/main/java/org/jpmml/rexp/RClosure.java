/*
 * Copyright (c) 2018 Villu Ruusmann
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

public class RClosure extends RExp {

	private RExp environment = null;

	private RPair parameters = null;

	private RExp body = null;


	public RClosure(RPair attributes, RExp environment, RPair parameters, RExp body){
		super(attributes);

		setEnvironment(environment);
		setParameters(parameters);
		setBody(body);
	}

	public RExp getEnvironment(){
		return this.environment;
	}

	private void setEnvironment(RExp environment){
		this.environment = environment;
	}

	public RPair getParameters(){
		return this.parameters;
	}

	private void setParameters(RPair parameters){
		this.parameters = parameters;
	}

	public RExp getBody(){
		return this.body;
	}

	private void setBody(RExp body){
		this.body = body;
	}
}