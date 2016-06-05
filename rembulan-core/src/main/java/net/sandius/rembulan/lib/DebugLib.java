package net.sandius.rembulan.lib;

import net.sandius.rembulan.core.Function;
import net.sandius.rembulan.core.LuaState;
import net.sandius.rembulan.core.Table;
import net.sandius.rembulan.util.Check;

/**
 * This library provides the functionality of the debug interface (see §4.9 of the Lua
 * Reference Manual) to Lua programs. You should exert care when using this library.
 * Several of its functions violate basic assumptions about Lua code (e.g., that variables
 * local to a function cannot be accessed from outside; that userdata metatables cannot
 * be changed by Lua code; that Lua programs do not crash) and therefore can compromise
 * otherwise secure code. Moreover, some functions in this library may be slow.
 *
 * All functions in this library are provided inside the {@code debug} table. All functions
 * that operate over a thread have an optional first argument which is the thread to operate
 * over. The default is always the current thread.
 */
public abstract class DebugLib implements Lib {

	@Override
	public void installInto(LuaState state, Table env) {
		Check.notNull(env);

		LibUtils.setIfNonNull(env, "debug", _debug());
		LibUtils.setIfNonNull(env, "gethook", _gethook());
		LibUtils.setIfNonNull(env, "getinfo", _getinfo());
		LibUtils.setIfNonNull(env, "getlocal", _getlocal());
		LibUtils.setIfNonNull(env, "getmetatable", _getmetatable());
		LibUtils.setIfNonNull(env, "getregistry", _getregistry());
		LibUtils.setIfNonNull(env, "getupvalue", _getupvalue());
		LibUtils.setIfNonNull(env, "getuservalue", _getuservalue());
		LibUtils.setIfNonNull(env, "sethook", _sethook());
		LibUtils.setIfNonNull(env, "setlocal", _setlocal());
		LibUtils.setIfNonNull(env, "setmetatable", _setmetatable());
		LibUtils.setIfNonNull(env, "setupvalue", _setupvalue());
		LibUtils.setIfNonNull(env, "setuservalue", _setuservalue());
		LibUtils.setIfNonNull(env, "traceback", _traceback());
		LibUtils.setIfNonNull(env, "upvalueid", _upvalueid());
		LibUtils.setIfNonNull(env, "upvaluejoin", _upvaluejoin());
	}

	/**
	 * {@code debug.debug ()}
	 *
	 * <p>Enters an interactive mode with the user, running each string that the user enters.
	 * Using simple commands and other debug facilities, the user can inspect global and local
	 * variables, change their values, evaluate expressions, and so on. A line containing only
	 * the word {@code cont} finishes this function, so that the caller continues
	 * its execution.</p>
	 *
	 * <p>Note that commands for {@code debug.debug} are not lexically nested within any
	 * function and so have no direct access to local variables.</p>
	 */
	public abstract Function _debug();

	/**
	 * {@code debug.gethook ([thread])}
	 *
	 * <p>Returns the current hook settings of the thread, as three values: the current hook
	 * function, the current hook mask, and the current hook count (as set by
	 * the {@link #_sethook() <code>debug.sethook</code>} function).</p>
	 */
	public abstract Function _gethook();

	/**
	 * {@code debug.getinfo ([thread,] f [, what])}
	 *
	 * <p>Returns a table with information about a function. You can give the function directly
	 * or you can give a number as the value of {@code f}, which means the function running
	 * at level {@code f} of the call stack of the given thread: level 0 is the current
	 * function ({@code getinfo} itself); level 1 is the function that called {@code getinfo}
	 * (except for tail calls, which do not count on the stack); and so on. If {@code f}
	 * is a number larger than the number of active functions, then {@code getinfo}
	 * returns <b>nil</b>.</p>
	 *
	 * <p>The returned table can contain all the fields returned by {@code lua_getinfo},
	 * with the string {@code what} describing which fields to fill in. The default for
	 * {@code what} is to get all information available, except the table of valid lines.
	 * If present, the option {@code 'f'} adds a field named {@code func} with the function
	 * itself. If present, the option {@code 'L'} adds a field named {@code activelines}
	 * with the table of valid lines.</p>
	 *
	 * <p>For instance, the expression {@code debug.getinfo(1,"n").name} returns a name for
	 * the current function, if a reasonable name can be found, and the expression
	 * {@code debug.getinfo(print)} returns a table with all available information about
	 * the {@code print} function.</p>
	 */
	public abstract Function _getinfo();

	/**
	 * {@code debug.getlocal ([thread,] f, local)}
	 *
	 * <p>This function returns the name and the value of the local variable with index local
	 * of the function at level {@code f} of the stack. This function accesses not only explicit
	 * local variables, but also parameters, temporaries, etc.</p>
	 *
	 * <p>The first parameter or local variable has index 1, and so on, following the order
	 * that they are declared in the code, counting only the variables that are active in
	 * the current scope of the function. Negative indices refer to vararg parameters;
	 * -1 is the first vararg parameter. The function returns <b>nil</b> if there is no variable
	 * with the given index, and raises an error when called with a level out of range.
	 * (You can call {@link #_getinfo() <code>debug.getinfo</code>} to check whether the level
	 * is valid.)</p>
	 *
	 * <p>Variable names starting with {@code '('} (open parenthesis) represent variables with
	 * no known names (internal variables such as loop control variables, and variables from
	 * chunks saved without debug information).</p>
	 *
	 * <p>The parameter {@code f} may also be a function. In that case, {@code getlocal}
	 * returns only the name of function parameters.</p>
	 */
	public abstract Function _getlocal();

	/**
	 * {@code debug.getmetatable (value)}
	 *
	 * <p>Returns the metatable of the given {@code value} or <b>nil</b> if it does not have
	 * a metatable.</p>
	 */
	public abstract Function _getmetatable();

	/**
	 * {@code debug.getregistry ()}
	 *
	 * <p>Returns the registry table (see §4.5 of the Lua Reference Manual).</p>
	 */
	public abstract Function _getregistry();

	/**
	 * {@code debug.getupvalue (f, up)}
	 *
	 * <p>This function returns the name and the value of the upvalue with index {@code up}
	 * of the function {@code f}. The function returns <b>nil</b> if there is no upvalue with
	 * the given index.</p>
	 *
	 * <p>Variable names starting with {@code '('} (open parenthesis) represent variables with
	 * no known names (variables from chunks saved without debug information).</p>
	 */
	public abstract Function _getupvalue();

	/**
	 * {@code debug.getuservalue (u)}
	 *
	 * <p>Returns the Lua value associated to {@code u}. If {@code u} is not a userdata,
	 * returns <b>nil</b>.</p>
	 */
	public abstract Function _getuservalue();

	/**
	 * {@code debug.sethook ([thread,] hook, mask [, count])}
	 *
	 * <p>Sets the given function as a hook. The string {@code mask} and the number {@code count}
	 * describe when the hook will be called. The string {@code mask} may have any combination
	 * of the following characters, with the given meaning:</p>
	 *
	 * <ul>
	 * <li><b>{@code 'c'}</b>: the hook is called every time Lua calls a function;</li>
	 * <li><b>{@code 'r'}</b>: the hook is called every time Lua returns from a function;</li>
	 * <li><b>{@code 'l'}</b>: the hook is called every time Lua enters a new line of code.</li>
	 * </ul>
	 *
	 * <p>Moreover, with a {@code count} different from zero, the hook is called also after
	 * every {@code count} instructions.</p>
	 *
	 * <p>When called without arguments, {@code debug.sethook} turns off the hook.</p>
	 *
	 * <p>When the hook is called, its first parameter is a string describing the event that
	 * has triggered its call: {@code "call"} (or {@code "tail call"}), {@code "return"},
	 * {@code "line"}, and {@code "count"}. For line events, the hook also gets the new line
	 * number as its second parameter. Inside a hook, you can call {@code getinfo} with level 2
	 * to get more information about the running function (level 0 is the {@code getinfo}
	 * function, and level 1 is the hook function).</p>
	 */
	public abstract Function _sethook();

	/**
	 * {@code debug.setlocal ([thread,] level, local, value)}
	 *
	 * <p>This function assigns the value {@code value} to the local variable with index
	 * {@code local} of the function at {@code level} level of the stack. The function returns
	 * <b>nil</b> if there is no local variable with the given index, and raises an error when
	 * called with a level out of range. (You can call {@link #_getinfo() <code>getinfo</code>}
	 * to check whether the level is valid.) Otherwise, it returns the name of the local
	 * variable.</p>
	 *
	 * <p>See {@link #_getlocal() <code>debug.getlocal</code>} for more information about variable
	 * indices and names.</p>
	 */
	public abstract Function _setlocal();

	/**
	 * {@code debug.setmetatable (value, table)}
	 *
	 * <p>Sets the metatable for the given {@code value} to the given {@code table} (which can
	 * be <b>nil</b>). Returns {@code value}.</p>
	 */
	public abstract Function _setmetatable();

	/**
	 * {@code debug.setupvalue (f, up, value)}
	 *
	 * <p>This function assigns the value {@code value} to the upvalue with index {@code up}
	 * of the function f. The function returns <b>nil</b> if there is no upvalue with the given
	 * index. Otherwise, it returns the name of the upvalue.</p>
	 */
	public abstract Function _setupvalue();

	/**
	 * {@code debug.setuservalue (udata, value)}
	 *
	 * <p>Sets the given {@code value} as the Lua value associated to the given {@code udata}.
	 * udata must be a full userdata.</p>
	 *
	 * <p>Returns {@code udata}.</p>
	 */
	public abstract Function _setuservalue();

	/**
	 * {@code debug.traceback ([thread,] [message [, level]])}
	 *
	 * <p>If {@code message} is present but is neither a string nor <b>nil</b>, this function
	 * returns message without further processing. Otherwise, it returns a string with
	 * a traceback of the call stack. The optional {@code message} string is appended at
	 * the beginning of the traceback. An optional {@code level} number tells at which level
	 * to start the traceback (default is 1, the function calling {@code traceback}).</p>
	 */
	public abstract Function _traceback();

	/**
	 * {@code debug.upvalueid (f, n)}
	 *
	 * <p>Returns a unique identifier (as a light userdata) for the upvalue numbered {@code n}
	 * from the given function.</p>
	 *
	 * <p>These unique identifiers allow a program to check whether different closures share
	 * upvalues. Lua closures that share an upvalue (that is, that access a same external
	 * local variable) will return identical ids for those upvalue indices.</p>
	 */
	public abstract Function _upvalueid();

	/**
	 * {@code debug.upvaluejoin (f1, n1, f2, n2)}
	 *
	 * <p>Make the {@code n1}-th upvalue of the Lua closure {@code f1} refer to
	 * the {@code n2}-th upvalue of the Lua closure {@code f2}.</p>
	 */
	public abstract Function _upvaluejoin();

}