package app.marlboroadvance.mpvex.ui.player

import java.io.File
import org.junit.Test
import org.luaj.vm2.lib.jse.JsePlatform

class PlayerStatisticsLuaTest {
  private fun script(): File = sequenceOf(
    File("src/main/assets/mpvex/stats.lua"),
    File("app/src/main/assets/mpvex/stats.lua"),
  ).first { it.isFile }

  @Test
  fun `resident show and hide execute the actual script state machine`() {
    val globals = JsePlatform.debugGlobals()
    globals.load(
      """
      test = {messages={}, bindings={}, timers={}, observers={}, prints={}, props={}, clears=0, terminated=0}
      local mp = {}
      package.preload['mp'] = function() return mp end
      package.preload['mp.utils'] = function() return {} end
      package.preload['mp.input'] = function()
          return {terminate=function() test.terminated=test.terminated+1 end, get=function() end}
      end
      package.preload['mp.options'] = function()
          return {read_options=function(o)
              o.plot_vsync_jitter=true
              o.plot_tonemapping_lut=true
              o.key_page_2='F2'
              o.key_page_5='F5'
          end}
      end
      mp.get_script_name = function() return 'stats' end
      mp.get_property_osd = function() return '' end
      mp.get_property_native = function(name)
          if name=='tone-mapping-visualize' then return test.props[name] or false end
          return test.props[name]
      end
      mp.set_property_native = function(name,value) test.props[name]=value end
      mp.observe_property = function(name,format,callback) test.observers[callback]=name end
      mp.unobserve_property = function(callback) test.observers[callback]=nil end
      mp.add_periodic_timer = function(timeout,callback)
          local timer={timeout=timeout, callback=callback, enabled=true, resumes=0}
          function timer:is_enabled() return self.enabled end
          function timer:kill() self.enabled=false end
          function timer:stop() self.enabled=false end
          function timer:resume() self.enabled=true; self.resumes=self.resumes+1 end
          test.timers[#test.timers+1]=timer
          return timer
      end
      mp.add_key_binding = function(key,name,callback) test.bindings[name]=callback end
      mp.add_forced_key_binding = mp.add_key_binding
      mp.remove_key_binding = function(name) test.bindings[name]=nil end
      mp.register_event = function() end
      mp.register_script_message = function(name,callback) test.messages[name]=callback end
      mp.osd_message = function(text) if text=='' then test.clears=test.clears+1 end end
      mp.set_osd_ass = function(w,h,text) if text=='' then test.clears=test.clears+1 end end
      """.trimIndent(),
      "fake-mpv",
    ).call()
    globals.load(script().readText(), "stats.lua").call()
    // Replace only drawing, keeping the real pages, timer, recorder and key-binding state machine.
    globals.load(
      """
      local function replace_upvalue(fn,name,value)
          for i=1,100 do
              local key=debug.getupvalue(fn,i)
              if not key then break end
              if key==name then debug.setupvalue(fn,i,value); return end
          end
          error('missing upvalue '..name)
      end
      local function upvalue(fn,name)
          for i=1,100 do
              local key,value=debug.getupvalue(fn,i)
              if not key then break end
              if key==name then return value end
          end
          error('missing upvalue '..name)
      end
      local function capture(page) test.prints[#test.prints+1]=page end
      replace_upvalue(upvalue(test.bindings['display-stats'],'process_key_binding'),'print_page',capture)
      replace_upvalue(test.messages['mpvex-show'],'print_page',capture)
      local display=test.timers[2]
      local cache=test.timers[1]
      local function recorder_count()
          local count=0
          for callback,name in pairs(test.observers) do if name=='vsync-jitter' then count=count+1 end end
          return count
      end
      local function assert_hidden()
          assert(not display:is_enabled() and not cache:is_enabled())
          assert(recorder_count()==0)
          for name in pairs(test.bindings) do assert(not name:find('__forced_',1,true)) end
          assert(test.props['tone-mapping-visualize']==false)
      end
      assert(test.props['user-data/mpvex/stats-ready']==true)
      test.messages['mpvex-show']('1')
      assert(display:is_enabled() and not display.oneshot and display.timeout==1)
      assert(cache:is_enabled() and recorder_count()==1)
      assert(test.props['tone-mapping-visualize']==true)
      assert(test.prints[#test.prints]=='1')
      local resumes=cache.resumes
      for _,page in ipairs({'2','3','5','1','1'}) do test.messages['mpvex-show'](page) end
      assert(display:is_enabled() and not display.oneshot)
      assert(cache.resumes==resumes and recorder_count()==1)
      assert(test.prints[#test.prints]=='1')
      assert(test.bindings['__forced_UP']==nil)
      test.messages['mpvex-show']('2')
      assert(test.prints[#test.prints]=='F2' and test.bindings['__forced_UP']~=nil)
      test.messages['mpvex-show']('3')
      assert(test.bindings['__forced_UP']==nil)
      test.messages['mpvex-show']('5')
      assert(test.prints[#test.prints]=='F5' and test.bindings['__forced_DOWN']~=nil)
      test.messages['mpvex-show']('4')
      assert(test.prints[#test.prints]=='F5')
      -- More than the original four-second preview lifetime must not hide resident stats.
      local before=test.clears
      for i=1,6 do display.callback() end
      assert(display:is_enabled() and test.clears==before)
      test.messages['mpvex-hide']()
      assert_hidden()
      local clears=test.clears
      test.messages['mpvex-hide']()
      assert(test.clears==clears)
      -- The legacy one-shot keyboard interface remains available, but touch show promotes it.
      test.bindings['display-page-3']()
      assert(display.oneshot and display:is_enabled())
      test.messages['mpvex-show']('2')
      assert(display:is_enabled() and not display.oneshot and recorder_count()==1)
      assert(test.bindings['__forced_ESC']~=nil)
      test.messages['mpvex-hide']()
      assert_hidden()
      test.bindings['display-stats']()
      assert(display.oneshot)
      test.messages['mpvex-hide']()
      assert_hidden()
      test.bindings['display-stats']()
      display:kill()
      display.callback()
      assert_hidden()
      """.trimIndent(),
      "statistics-state-assertions",
    ).call()
  }
}
