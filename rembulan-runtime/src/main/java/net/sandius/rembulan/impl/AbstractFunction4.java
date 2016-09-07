/*
 * Copyright 2016 Miroslav Janíček
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sandius.rembulan.impl;

import net.sandius.rembulan.ExecutionContext;
import net.sandius.rembulan.runtime.Function;
import net.sandius.rembulan.runtime.ResolvedControlThrowable;

/**
 * Abstract function of four arguments.
 */
public abstract class AbstractFunction4 extends Function {

	@Override
	public void invoke(ExecutionContext context) throws ResolvedControlThrowable {
		invoke(context, null, null, null, null);
	}

	@Override
	public void invoke(ExecutionContext context, Object arg1) throws ResolvedControlThrowable {
		invoke(context, arg1, null, null, null);
	}

	@Override
	public void invoke(ExecutionContext context, Object arg1, Object arg2) throws ResolvedControlThrowable {
		invoke(context, arg1, arg2, null, null);
	}

	@Override
	public void invoke(ExecutionContext context, Object arg1, Object arg2, Object arg3) throws ResolvedControlThrowable {
		invoke(context, arg1, arg2, arg3, null);
	}

	@Override
	public void invoke(ExecutionContext context, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) throws ResolvedControlThrowable {
		invoke(context, arg1, arg2, arg3, arg4);
	}

	@Override
	public void invoke(ExecutionContext context, Object[] args) throws ResolvedControlThrowable {
		Object a = null, b = null, c = null, d = null;
		switch (args.length) {
			default:
			case 4: d = args[3];
			case 3: c = args[2];
			case 2: b = args[1];
			case 1: a = args[0];
			case 0:
		}
		invoke(context, a, b, c, d);
	}

}
